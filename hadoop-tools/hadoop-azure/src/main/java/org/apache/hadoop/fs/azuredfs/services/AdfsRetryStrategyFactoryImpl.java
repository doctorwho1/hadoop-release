package org.apache.hadoop.fs.azuredfs.services;

import javax.inject.Inject;

import com.microsoft.rest.retry.ExponentialBackoffRetryStrategy;
import com.microsoft.rest.retry.RetryStrategy;

import org.apache.hadoop.fs.azuredfs.constants.ConfigurationKeys;
import org.apache.hadoop.fs.azuredfs.constants.FileSystemConfigurations;
import org.apache.hadoop.fs.azuredfs.contracts.exceptions.AzureDistributedFileSystemException;
import org.apache.hadoop.fs.azuredfs.contracts.services.ConfigurationService;
import org.apache.hadoop.fs.azuredfs.contracts.services.AdfsRetryStrategyFactory;

class AdfsRetryStrategyFactoryImpl implements AdfsRetryStrategyFactory{
  private final ConfigurationService configurationService;

  @Inject
  public AdfsRetryStrategyFactoryImpl(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  @Override
  public RetryStrategy create() throws AzureDistributedFileSystemException {
    int numRetries = configurationService.getConfiguration().getInt(ConfigurationKeys.KEY_MAX_IO_RETRIES,
        FileSystemConfigurations.DEFAULT_MAX_RETRY_ATTEMPTS);
    int minBackoff = configurationService.getConfiguration().getInt(ConfigurationKeys.KEY_MIN_BACKOFF_INTERVAL,
        FileSystemConfigurations.DEFAULT_MIN_BACKOFF_INTERVAL);
    int maxBackoff = configurationService.getConfiguration().getInt(ConfigurationKeys.KEY_MAX_BACKOFF_INTERVAL,
        FileSystemConfigurations.DEFAULT_MAX_BACKOFF_INTERVAL);
    int deltaBackoff = configurationService.getConfiguration().getInt(ConfigurationKeys.KEY_BACKOFF_INTERVAL,
        FileSystemConfigurations.DEFAULT_BACKOFF_INTERVAL);

    return new ExponentialBackoffRetryStrategy(
        numRetries,
        minBackoff,
        maxBackoff,
        deltaBackoff);
  }
}
