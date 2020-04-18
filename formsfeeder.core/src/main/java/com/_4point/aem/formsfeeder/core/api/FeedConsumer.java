package com._4point.aem.formsfeeder.core.api;

import com._4point.aem.formsfeeder.core.datasource.DataSourceList;

/**
 * FeedConsumer is an interface that is implemented by classes that wish to consume a feed from a FeedProducer.
 *
 */
@FunctionalInterface
public interface FeedConsumer {

	/**
	 * Processes a list of datasources and returns a result in the form of another list of datasources.
	 * 
	 * It is up to the caller to decide how to process the returned list based on the individual data source names.
	 * 
	 * @param dataSources
	 * @return A list of datasources that contain the results of the operation.
	 * @throws FeedConsumerException
	 */
	public DataSourceList accept(DataSourceList dataSources) throws FeedConsumerException;
	
	/**
	 * Abstract parent of exceptions generated by the FeedConsumer. 
	 *
	 */
	@SuppressWarnings("serial")
	public static abstract class FeedConsumerException extends Exception {
		
		public enum FailureAction {
			FAIL, RETRY;
		}

		private final FailureAction action;
		
		public FeedConsumerException() {
			super();
			this.action = FailureAction.FAIL;
		}

		public FeedConsumerException(String message, Throwable cause) {
			super(message, cause);
			this.action = FailureAction.FAIL;
		}

		public FeedConsumerException(String message) {
			super(message);
			this.action = FailureAction.FAIL;
		}

		public FeedConsumerException(Throwable cause) {
			super(cause);
			this.action = FailureAction.FAIL;
		}
		public FeedConsumerException(FailureAction action) {
			super();
			this.action = action;
		}

		public FeedConsumerException(String message, Throwable cause, FailureAction action) {
			super(message, cause);
			this.action = action;
		}

		public FeedConsumerException(String message, FailureAction action) {
			super(message);
			this.action = action;
		}

		public FeedConsumerException(Throwable cause, FailureAction action) {
			super(cause);
			this.action = action;
		}

		public final FailureAction action() {
			return action;
		}
	}
	
	/**
	 * Exceptions that are caused by an invalid or malformed input request.
	 * 
	 * Requests that cause these exceptions can be corrected by whatever creates the request.
	 *
	 */
	@SuppressWarnings("serial")
	public static class FeedConsumerBadRequestException extends FeedConsumerException {

		private FeedConsumerBadRequestException() {
			super();
		}

		private FeedConsumerBadRequestException(FailureAction action) {
			super(action);
		}

		private FeedConsumerBadRequestException(String message, FailureAction action) {
			super(message, action);
		}

		private FeedConsumerBadRequestException(String message, Throwable cause, FailureAction action) {
			super(message, cause, action);
		}

		private FeedConsumerBadRequestException(String message, Throwable cause) {
			super(message, cause);
		}

		private FeedConsumerBadRequestException(String message) {
			super(message);
		}

		private FeedConsumerBadRequestException(Throwable cause, FailureAction action) {
			super(cause, action);
		}

		private FeedConsumerBadRequestException(Throwable cause) {
			super(cause);
		}
	}
	
	/**
	 * Exceptions that are caused by factors outside the control of the caller.
	 * 
	 * These exceptions are not directly related to the input data but more likely caused by
	 * internal or environmental issues.
	 *
	 */
	@SuppressWarnings("serial")
	public static class FeedConsumerInternalErrorException extends FeedConsumerException {

		private FeedConsumerInternalErrorException() {
			super();
		}

		private FeedConsumerInternalErrorException(FailureAction action) {
			super(action);
		}

		private FeedConsumerInternalErrorException(String message, FailureAction action) {
			super(message, action);
		}

		private FeedConsumerInternalErrorException(String message, Throwable cause, FailureAction action) {
			super(message, cause, action);
		}

		private FeedConsumerInternalErrorException(String message, Throwable cause) {
			super(message, cause);
		}

		private FeedConsumerInternalErrorException(String message) {
			super(message);
		}

		private FeedConsumerInternalErrorException(Throwable cause, FailureAction action) {
			super(cause, action);
		}

		private FeedConsumerInternalErrorException(Throwable cause) {
			super(cause);
		}
		
	}
}
