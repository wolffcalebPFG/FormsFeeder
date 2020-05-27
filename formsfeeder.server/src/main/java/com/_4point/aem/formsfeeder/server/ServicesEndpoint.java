package com._4point.aem.formsfeeder.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com._4point.aem.formsfeeder.core.api.FeedConsumer;
import com._4point.aem.formsfeeder.core.api.FeedConsumer.FeedConsumerBadRequestException;
import com._4point.aem.formsfeeder.core.api.FeedConsumer.FeedConsumerException;
import com._4point.aem.formsfeeder.core.api.FeedConsumer.FeedConsumerInternalErrorException;
import com._4point.aem.formsfeeder.core.datasource.DataSource;
import com._4point.aem.formsfeeder.core.datasource.DataSourceList;
import com._4point.aem.formsfeeder.core.datasource.DataSourceList.Builder;
import com._4point.aem.formsfeeder.core.datasource.MimeType;
import com._4point.aem.formsfeeder.server.pf4j.FeedConsumers;
import com._4point.aem.formsfeeder.server.support.CorrelationId;
import com._4point.aem.formsfeeder.server.support.FfLoggerFactory;

/**
 * @author rob.mcdougall
 *
 */
/**
 * @author rob.mcdougall
 *
 */
/**
 * @author rob.mcdougall
 *
 */
@Path(ServicesEndpoint.API_V1_PATH)
public class ServicesEndpoint {
	private final static Logger baseLogger = LoggerFactory.getLogger(ServicesEndpoint.class);
	
	// Path that all plug-in services reside under.  The Remainder is used capture the name of the plug-in to be invoked.
	/* package */ static final String API_V1_PATH = "/api/v1";
	private static final String PLUGIN_NAME_REMAINDER_PATH = "/{remainder : .+}";
	
	// Prefix used on DataSource names generated by fluentforms.  This is to make sure that they do not clash with
	// names provided by user applications.  Users should avoid using this prefix.
	private static final String FORMSFEEDER_PREFIX = "formsfeeder:";

	// Data Source name we use to pass in the correlation id.
	private static final String FORMSFEEDER_CORRELATION_ID_DS_NAME = FORMSFEEDER_PREFIX + CorrelationId.CORRELATION_ID_HDR;

	// Data Source Name we use to pass in the bytes from a POST body that does not include name. 
	private static final String FORMSFEEDER_BODY_BYTES_DS_NAME = FORMSFEEDER_PREFIX + "BodyBytes";
	
	// Attribute that is used for Content Disposition
	private static final String FORMSFEEDER_CONTENT_DISPOSITION_ATTRIBUTE = FORMSFEEDER_PREFIX + "Content-Disposition"; 
	
	@Autowired
	private FeedConsumers feedConsumers;
	
	/**
	 * Method that gets invoked for all GET transactions
	 *  
	 * This converts the query parameters into DataSources and then calls the appropriate plug-in.  It then returns
	 * the results of the plug-in as either a single response (if the plug-in returned just one DataSource) or as a
	 * multipart/form-data response (if the plug-in returned multiple DataSources).
	 * 
	 * @param remainder
	 * @param correlationIdHdr
	 * @param uriInfo
	 * @return
	 */
	@Path(PLUGIN_NAME_REMAINDER_PATH)
	@GET
    public Response invokeNoBody(@PathParam("remainder") String remainder, @Context HttpHeaders httpHeaders, @HeaderParam(CorrelationId.CORRELATION_ID_HDR) final String correlationIdHdr, @Context UriInfo uriInfo) {
		final String correlationId = CorrelationId.generate(correlationIdHdr);
		final Logger logger = FfLoggerFactory.wrap(correlationId, baseLogger);
		logger.info("Recieved GET request to '" + API_V1_PATH + "/" + remainder + "'.");
		if (logger.isDebugEnabled()) {
			for( Entry<String, List<String>> headers : httpHeaders.getRequestHeaders().entrySet()) {
				String key = headers.getKey();
				for (String value : headers.getValue()) {
					logger.debug("HttpHeader->'" + key + "'='" + value + "'.");
				}
			}
		}
		final DataSourceList dataSourceList1 = convertQueryParamsToDataSourceList(uriInfo.getQueryParameters().entrySet(), logger);
		final DataSourceList dataSourceList2 = generateFormsFeederDataSourceList(correlationId);
		return invokePlugin(remainder, DataSourceList.from(dataSourceList1, dataSourceList2), logger, correlationId);
	}

	/**
	 * Method that gets invoked for POST transactions that contain multipart/form-data.
	 * 
	 * This breaks apart the multipart/form-data into fields, converts the fields to DataSources.  Merges the DataSources
	 * from the query parameters with the field DataSources and then calls the appropriate plug-in.  It then returns
	 * the results of the plug-in as either a single response (if the plug-in returned just one DataSource) or as a
	 * multipart/form-data response (if the plug-in returned multiple DataSources).
	 * 
	 * @param remainder
	 * @param correlationIdHdr
	 * @param uriInfo
	 * @param formData
	 * @return
	 * @throws IOException 
	 */
	@Path(PLUGIN_NAME_REMAINDER_PATH)
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@POST
    public Response invokeWithMultipartFormDataBody(@PathParam("remainder") String remainder, @Context HttpHeaders httpHeaders, @HeaderParam(CorrelationId.CORRELATION_ID_HDR) final String correlationIdHdr, @Context UriInfo uriInfo, FormDataMultiPart formData) throws IOException {
		final String correlationId = CorrelationId.generate(correlationIdHdr);
		final Logger logger = FfLoggerFactory.wrap(correlationId, baseLogger);
		logger.info("Received " + MediaType.MULTIPART_FORM_DATA + " POST request to '" + API_V1_PATH + "/" + remainder + "'.");
		if (logger.isDebugEnabled()) {
			for( Entry<String, List<String>> headers : httpHeaders.getRequestHeaders().entrySet()) {
				String key = headers.getKey();
				for (String value : headers.getValue()) {
					logger.debug("HttpHeader->'" + key + "'='" + value + "'.");
				}
			}
		}
		final DataSourceList dataSourceList1 = convertMultipartFormDataToDataSourceList(formData, logger);
		final DataSourceList dataSourceList2 = convertQueryParamsToDataSourceList(uriInfo.getQueryParameters().entrySet(), logger);
		final DataSourceList dataSourceList3 = generateFormsFeederDataSourceList(correlationId);
		return invokePlugin(remainder, DataSourceList.from(dataSourceList1, dataSourceList2, dataSourceList3), logger, correlationId);
	}

	/**
	 * Method that gets invoked for POST transactions that contain anything other than multipart/form-data
	 *  
	 * This converts the body of the incoming POST to single DataSources.  It then merges that DataSource with
	 * the DataSource from the query parameters and then calls the appropriate plug-in.  It then returns
	 * the results of the plug-in as either a single response (if the plug-in returned just one DataSource) or as a
	 * multipart/form-data response (if the plug-in returned multiple DataSources).
	 * 
	 * @param remainder
	 * @param httpHeaders
	 * @param correlationIdHdr
	 * @param uriInfo
	 * @param in
	 * @return
	 * @throws IOException
	 */
	@Path(PLUGIN_NAME_REMAINDER_PATH)
	@Consumes(MediaType.WILDCARD)
	@POST
    public Response invokeWithBody(@PathParam("remainder") String remainder, @Context HttpHeaders httpHeaders, @HeaderParam(CorrelationId.CORRELATION_ID_HDR) final String correlationIdHdr, @Context UriInfo uriInfo, InputStream in) throws IOException {
		final String correlationId = CorrelationId.generate(correlationIdHdr);
		final Logger logger = FfLoggerFactory.wrap(correlationId, baseLogger);
		MediaType mediaType = httpHeaders.getMediaType();
		logger.info("Received '" + mediaType.toString() + "' POST request to '" + API_V1_PATH + "/" + remainder + "'.");
		if (logger.isDebugEnabled()) {
			for( Entry<String, List<String>> headers : httpHeaders.getRequestHeaders().entrySet()) {
				String key = headers.getKey();
				for (String value : headers.getValue()) {
					logger.debug("HttpHeader->'" + key + "'='" + value + "'.");
				}
			}
		}
		try {
			final ContentDisposition contentDisposition = determineContentDisposition(httpHeaders);
			final DataSourceList dataSourceList1 = convertBodyToDataSourceList(in, mediaType, contentDisposition, logger);
			final DataSourceList dataSourceList2 = convertQueryParamsToDataSourceList(uriInfo.getQueryParameters().entrySet(), logger);
			final DataSourceList dataSourceList3 = generateFormsFeederDataSourceList(correlationId);
			return invokePlugin(remainder, DataSourceList.from(dataSourceList1, dataSourceList2, dataSourceList3), logger, correlationId);
		} catch (ContentDispositionHeaderException e) {
			// If we encounter Parse Errors while determining ContentDisposition, it must be a BadRequest.
			logger.error(e.getMessage() + ", Returning \"Bad Request\" status code.", e);
			return buildResponse(Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN_TYPE), correlationId);
		}
	}

	/**
	 * Determines if there is a plug-in associated with an Url provided and, if so, then invokes that plug-in.  Also
	 * captures any exceptions that a plugin throws and converts it to a response.
	 * 
	 * We're intentionally sparse in the information about exceptions that we return to the client for security reasons.
	 * We just pass back the exception message.  Full details (and a stack trace) are written to the log.  That's where
	 * someone should go in order to get a fuller picture of what the issue is.
	 * 
	 * @param remainder
	 * @param dataSourceList
	 * @param logger
	 * @return
	 */
	private final Response invokePlugin(final String remainder, final DataSourceList dataSourceList, final Logger logger, final String correlationId) {
		Optional<FeedConsumer> optConsumer = feedConsumers.consumer(determineConsumerName(remainder));
		if (optConsumer.isEmpty()) {
			String msg = "Resource '" + API_V1_PATH + "/" + remainder + "' does not exist.";
			logger.error(msg + " Returning \"Not Found\" status code.");
			return buildResponse(Response.status(Response.Status.NOT_FOUND).entity(msg).type(MediaType.TEXT_PLAIN_TYPE), correlationId);
		} else {
			try {
				return convertToResponse(invokeConsumer(dataSourceList, optConsumer.get(), logger), logger, correlationId);
			} catch (FeedConsumerInternalErrorException e) {
				String msg = String.format("Plugin processor experienced an Internal Server Error. (%s)", e.getMessage());
				logger.error(msg + ", Returning \"Internal Server Error\" status code.",e);
				return buildResponse(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).type(MediaType.TEXT_PLAIN_TYPE), correlationId);
			} catch (FeedConsumerBadRequestException e) {
				String msg = String.format("Plugin processor detected Bad Request. (%s)", e.getMessage());
				logger.error(msg + ", Returning \"Bad Request\" status code.", e);
				return buildResponse(Response.status(Response.Status.BAD_REQUEST).entity(msg).type(MediaType.TEXT_PLAIN_TYPE), correlationId);
			} catch (FeedConsumerException e) {
				String msg = String.format("Plugin processor error. (%s)", e.getMessage());
				logger.error(msg + ", Returning \"Internal Server Error\" status code.", e);
				return buildResponse(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).type(MediaType.TEXT_PLAIN_TYPE), correlationId);
			} catch (Exception e) {
				String msg = String.format("Error within Plugin processor. (%s)", e.getMessage());
				logger.error(msg + ", Returning \"Internal Server Error\" status code.", e);
				return buildResponse(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).type(MediaType.TEXT_PLAIN_TYPE), correlationId);
			}
		}
	}
	
	/**
	 * Converts the incoming Query Parameters into a DataSourceList so that they can be processed by a plug-in
	 * 
	 * @param queryParams
	 * @return
	 */
	private static final DataSourceList convertQueryParamsToDataSourceList(final Collection<Entry<String, List<String>>> queryParams, final Logger logger) {
		Builder builder = DataSourceList.builder();
		for (Entry<String, List<String>> entry : queryParams) {
			String name = entry.getKey();
			for(String value : entry.getValue()) {
				logger.debug("Found Query Parameter '" + name + "'.");
				builder.add(name, value);
			}
		}
		return builder.build();
	}

	/**
	 * For now, this is a nop, however it's here in case we want to map the paths to FeedConsumer names in the future.
	 * 
	 * @param remainder
	 * @return
	 */
	private static final String determineConsumerName(final String remainder) {
		return remainder;
	}

	/**
	 * Determine the contentDisposition from the HTTP Headers of a single body request.
	 * 
	 * @param httpHeaders
	 * @return
	 * @throws ContentDispositionHeaderException 
	 */
	private static ContentDisposition determineContentDisposition(HttpHeaders httpHeaders) throws ContentDispositionHeaderException {
		try {
			List<String> contentDispositions = httpHeaders.getRequestHeader(HttpHeaders.CONTENT_DISPOSITION);
			if (contentDispositions != null && !contentDispositions.isEmpty()) {
				return new ContentDisposition(contentDispositions.get(0));
			} else {
				return null;
			}
		} catch (ParseException e) {
			String msg = "Error while parsing " + HttpHeaders.CONTENT_DISPOSITION + " header (" + e.getMessage() + "). '" + HttpHeaders.CONTENT_DISPOSITION + ": " + httpHeaders.getHeaderString(HttpHeaders.CONTENT_DISPOSITION)+ "'";
			throw new ContentDispositionHeaderException(msg, e);
		}
	}

	/**
	 * Converts the incoming multipart/form-data into a DataSourceList so that they can be processed by a plug-in
	 * 
	 * @param formData
	 * @return
	 * @throws IOException 
	 */
	private static final DataSourceList convertMultipartFormDataToDataSourceList(final FormDataMultiPart formData, final Logger logger) throws IOException {
		Builder builder = DataSourceList.builder();
		for (Entry<String, List<FormDataBodyPart>> entry : formData.getFields().entrySet()) {
			String name = entry.getKey();
			for(FormDataBodyPart part : entry.getValue()) {
				if (part.isSimple()) {
					logger.debug("Found simple Form Data Part '" + name + "' (" + part.getName() + ").");
					builder.add(name, part.getValue());
				} else {
					logger.debug("Found complex Form Data Part '" + name + "' (" + part.getName() + ").");
					ContentDisposition contentDisposition = part.getContentDisposition();
					String fileName = contentDisposition.getFileName();
					if (logger.isDebugEnabled()) {
						Date creationDate = contentDisposition.getCreationDate();
						Date modificationDate = contentDisposition.getModificationDate();
						Date readDate = contentDisposition.getReadDate();
						logger.debug("    Filename='" + fileName + "'.");
						logger.debug("    CreationDate='" + (creationDate != null ? creationDate.toString() : "null") + "'.");
						logger.debug("    ModificationDate='" + (modificationDate != null ? modificationDate : "null") + "'.");
						logger.debug("    ReadDate='" + (readDate != null ? readDate : "null") + "'.");
					}
					// TODO: This is a naive implementation that just reads the whole InputStream into memory.  Should fix this.
					if (fileName != null) {
						builder.add(name, part.getEntityAs(InputStream.class).readAllBytes(), fromMediaType(part.getMediaType()), Paths.get(fileName));
					} else {
						builder.add(name, part.getEntityAs(InputStream.class).readAllBytes(), fromMediaType(part.getMediaType()));
					}
				}
			}
		}
		return builder.build();
	}
	
	/**
	 * This is a rather naive implementation.  It reads the incoming data into memory.
	 * 
	 * A better implementation would create a DataSource from the inputStream but that would require a little more
	 * work, so I am postponing that until later.
	 * 
	 * TODO: Create a better implementation for this.
	 * 
	 * @param in
	 * @param contentType
	 * @return
	 * @throws IOException
	 */
	private static final DataSourceList convertBodyToDataSourceList(final InputStream in, final MediaType contentType, final ContentDisposition contentDisposition, final Logger logger) throws IOException {
		logger.debug("Found Body Parameter of type '" + contentType.toString() + "'.");
		String filename = contentDisposition != null ? contentDisposition.getFileName() : null;
		if (filename != null) {
			return DataSourceList.builder().add(FORMSFEEDER_BODY_BYTES_DS_NAME, in.readAllBytes(), fromMediaType(contentType), Paths.get(filename)).build();
		} else {
			return DataSourceList.builder().add(FORMSFEEDER_BODY_BYTES_DS_NAME, in.readAllBytes(), fromMediaType(contentType)).build();
		}
	}

	/**
	 * Invokes a FeedConsumer provided by a plug-in.
	 * 
	 * @param inputs
	 * @param consumer
	 * @return
	 * @throws FeedConsumerException
	 */
	private static final DataSourceList invokeConsumer(final DataSourceList inputs, final FeedConsumer consumer, final Logger logger) throws FeedConsumerException {
		logger.debug("Before calling Plugin");
		DataSourceList accept = consumer.accept(inputs);
		logger.debug("After calling Plugin");
		return accept;
	}
	
	/**
	 * Converts the DataSourceList returned by a plug-in to a Response that will get sent back to the caller
	 * 
	 * @param outputs
	 * @param logger
	 * @return
	 */
	private static final Response convertToResponse(final DataSourceList outputs, final Logger logger, final String correlationId) {
		List<DataSource> dsList = Objects.requireNonNull(outputs, "Plugin returned null DataSourceList!").list();
		if (dsList.isEmpty()) {
			// Nothing in the response, so return no content.
	    	logger.debug("Returning no data sources.");
			return buildResponse(Response.noContent(), correlationId);
		} else if (dsList.size() == 1) {
			// One data source, so return the contents in the body of the response.
			return convertDataSourceToResponse(outputs.list().get(0), correlationId, logger);
		} else { // More than one return.
			// Convert DataSourceList to MultipartFormData.
	    	FormDataMultiPart responsesData = new FormDataMultiPart();
	    	for(var dataSource : outputs.list()) {
				addFormDataPart(responsesData, dataSource);
	    	}
	    	logger.debug("Returning multiple data sources.");
			for (var bp : responsesData.getBodyParts()) {
				logger.debug("Added {} -> {}", bp.getMediaType().toString(), bp.getContentDisposition());
			}
			logger.debug("Responses mediatype='{}'.", responsesData.getMediaType().toString());
			return buildResponse(Response.ok(responsesData, responsesData.getMediaType()), correlationId);
		}
	}

	/**
	 * Build a response from a ResponseBuilder.  This is mainly to make sure that all responses contain the correlationId in them.
	 * 
	 * @param builder
	 * @param correlationId
	 * @return
	 */
	private static final Response buildResponse(final ResponseBuilder builder, final String correlationId) {
		builder.header(CorrelationId.CORRELATION_ID_HDR, correlationId);
		return builder.build();
	}
	
	/**
	 * Converts a FormsFeeder core MimeType object into JAX-RS MediaType object.
	 * 
	 * @param mimeType
	 * @return
	 */
	private static final MediaType fromMimeType(final MimeType mimeType) {
		Charset charset = mimeType.charset();
		if (charset != null) {
			return new MediaType(mimeType.type(), mimeType.subtype(), charset.name());
		} else {
			return new MediaType(mimeType.type(), mimeType.subtype());
		}
	}
	
	/**
	 * Converts a JAX-RS MediaType object into FormsFeeder core MimeType object.
	 * 
	 * @param mediaType
	 * @return
	 */
	private static final MimeType fromMediaType(final MediaType mediaType) {
		return MimeType.of(mediaType.toString());
	}

	/**
	 * Generates a DataSourceList containing variables that are generated by the FormsFeeder server.
	 * 
	 * @param correlationId
	 * @return
	 */
	private static final DataSourceList generateFormsFeederDataSourceList(final String correlationId) {
		return DataSourceList.builder()
				.add(FORMSFEEDER_CORRELATION_ID_DS_NAME, correlationId)
				.build();
	}
	
	/**
	 * Convert a single DataSource into a Response object that will be returned to the user.
	 * 
	 * @param dataSource	The single DataSource object to be converted
	 * @param correlationId	The current correlation id
	 * @param logger		The current logger
	 * @return				The response that will be returned to the user.
	 */
	private static Response convertDataSourceToResponse(DataSource dataSource, final String correlationId, final Logger logger) {
		MediaType mediaType = fromMimeType(dataSource.contentType());
		logger.debug("Returning one data source. mediatype='{}'.", mediaType.toString());
		ResponseBuilder responseBuilder = Response.ok(dataSource.inputStream(), mediaType);
		
		// If a Content-Disposition attribute is present on the datasource, use it, otherwise default to "inline"
		String contentDispositionType = Optional.ofNullable(dataSource.attributes().get(FORMSFEEDER_CONTENT_DISPOSITION_ATTRIBUTE)).orElse("inline");
		
		// If a filename is present, then add a Content-Disposition header to the response.
		dataSource.filename()
				  .map((f)->contentDispositionFromFilename(contentDispositionType, f))
				  .ifPresent((cd)->responseBuilder.header(HttpHeaders.CONTENT_DISPOSITION, cd.toString()));
		
		return buildResponse(responseBuilder, correlationId);
	}

	/**
	 * Map a filename into a Content-Disposition header.
	 * 
	 * @param filename
	 * @return
	 */
	private static final ContentDisposition contentDispositionFromFilename(String type, java.nio.file.Path filename) {
		return ContentDisposition.type(type).fileName(filename.getFileName().toString()).build();
	}

	/**
	 * Add FormDataPart to the FormDataMultiPart response based on the DataSource.
	 * 
	 * @param responsesData
	 * @param dataSource
	 * @return
	 */
	private static final FormDataMultiPart addFormDataPart(FormDataMultiPart responsesData, DataSource dataSource) {
		Optional<java.nio.file.Path> optFilename = dataSource.filename();
		if (optFilename.isPresent()) {
			java.nio.file.Path filename = optFilename.get();
			FormDataContentDisposition cd = FormDataContentDisposition.name(dataSource.name()).fileName(filename.getFileName().toString()).build();
			responsesData.bodyPart(new FormDataBodyPart(cd, dataSource.inputStream(), fromMimeType(dataSource.contentType())));
		} else {
			responsesData.field(dataSource.name(), dataSource.inputStream(), fromMimeType(dataSource.contentType()));
		}
		return responsesData;
	}

	/**
	 * Exceptions that occur while performing ContentDisposition processing. 
	 *
	 */
	@SuppressWarnings("serial")
	private static class ContentDispositionHeaderException extends Exception {
		private ContentDispositionHeaderException() {
			super();
		}

		private ContentDispositionHeaderException(String message, Throwable cause) {
			super(message, cause);
		}

		private ContentDispositionHeaderException(String message) {
			super(message);
		}

		private ContentDispositionHeaderException(Throwable cause) {
			super(cause);
		}
		
	}
}
