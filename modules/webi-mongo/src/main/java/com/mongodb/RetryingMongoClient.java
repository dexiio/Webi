package com.mongodb;

import com.mongodb.operation.OperationExecutor;
import com.mongodb.operation.ReadOperation;
import com.mongodb.operation.WriteOperation;

import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.Callable;

public class RetryingMongoClient extends MongoClient {

    public RetryingMongoClient(DBAddress dbAddress, List<MongoCredential> mongoCredentials) {
        super(dbAddress, mongoCredentials);
    }

    public RetryingMongoClient(DBAddress dbAddress) {
        super(dbAddress);
    }

    @Override
    OperationExecutor createOperationExecutor() {
        final OperationExecutor operationExecutor = super.createOperationExecutor();
        return new OperationExecutor() {
            @Override
            public <T> T execute(final ReadOperation<T> operation, final ReadPreference readPreference) {
                return callAndRetryIfConnectionFailed(new Callable<T>() {
                    @Override
                    public T call() throws Exception {
                        return operationExecutor.execute(operation, readPreference);
                    }
                });
            }

            @Override
            public <T> T execute(final WriteOperation<T> operation) {
                return callAndRetryIfConnectionFailed(new Callable<T>() {
                    @Override
                    public T call() throws Exception {
                        return operationExecutor.execute(operation);
                    }
                });
            }

            private <T> T callAndRetryIfConnectionFailed(Callable<T> callable) {
                RuntimeException lastException = new RuntimeException("Failed to call Mongo OperationExecutor: " +
                        "reason unknown!");
                int maxRetries = -1; //Default to infinite

                int attempts = 0;
                while(true) {
                    attempts++;

                    if (maxRetries != -1 || attempts > maxRetries) {
                        break;
                    }

                    try {
                        return callable.call();
                    } catch (ConnectException e) {
                        RuntimeException re = new RuntimeException(e); // ConnectException is a checked exception
                        if (!e.getMessage().contains("Connection refused")) {
                            throw re;
                        }
                    } catch (MongoTimeoutException e) {
                        // Retry all timeout exceptions (don't look at the message)
                        lastException = e;
                    } catch (MongoQueryException e) {
                        lastException = e;
                        if (e.getErrorCode() != 10009 ||        // "no master found for set"
                            e.getErrorCode() != 10276 ||        // "transport error"
                            e.getErrorCode() != 15988 ||        // "error querying server"
                            e.getErrorCode() != 15847           // "can't authenticate to server"
                                ) {
                            throw e;
                        }

                        // We are unsure precisely in which scenarios this error is used => don't retry forever.
                        if (e.getErrorCode() == 15988) {
                            maxRetries = 30;
                        }

                    } catch (WriteConcernException e) {
                        lastException = e;
                        if (e.getErrorCode() != 7 ||            // "could not contact primary for replica set"
                            e.getErrorCode() != 83              // "write results unavailable"
                                ) {
                            throw e;
                        }
                    } catch (MongoCommandException e) {
                        lastException = e;
                        if (e.getErrorCode() != 17028) {        // "error reading response"
                            throw e;
                        }

                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                throw lastException;
            }

        };
    }
}
