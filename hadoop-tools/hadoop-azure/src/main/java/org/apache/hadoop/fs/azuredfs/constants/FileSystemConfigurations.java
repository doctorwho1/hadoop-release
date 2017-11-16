/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.azuredfs.constants;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

/**
 * Responsible to keep all the Azure Distributed Filesystem related configurations.
 */
@InterfaceAudience.Public
@InterfaceStability.Evolving
public final class FileSystemConfigurations {
  public static final String USER_HOME_DIRECTORY_PREFIX = "/user";
  public static final int FS_AZURE_DEFAULT_CONNECTION_TIMEOUT = 90;
  public static final int FS_AZURE_DEFAULT_CONNECTION_READ_TIMEOUT = 90;
  public static final String HDI_IS_FOLDER = "hdi_isfolder";

  // Retry parameter defaults.
  public static final int DEFAULT_MIN_BACKOFF_INTERVAL = 3 * 1000;  // 3s
  public static final int DEFAULT_MAX_BACKOFF_INTERVAL = 30 * 1000;  // 30s
  public static final int DEFAULT_BACKOFF_INTERVAL = 3 * 1000;  // 3s
  public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 30;
  public static final String ADFS_EMULATOR_TARGET_STORAGE_VERSION = "2017-11-09";
  public static final String ADFS_TARGET_STORAGE_VERSION = "2017-04-17";

  private static final int ONE_KB = 1024;
  private static final int ONE_MB = ONE_KB * ONE_KB;

  // Default upload and download buffer size
  public static final int DEFAULT_WRITE_BUFFER_SIZE = 4 * ONE_MB;  // 4 MB
  public static final int DEFAULT_READ_BUFFER_SIZE = 4 * ONE_MB;  // 4 MB
  public static final int MIN_BUFFER_SIZE = 16 * ONE_KB;  // 16 KB
  public static final int MAX_BUFFER_SIZE = 100 * ONE_MB;  // 100 MB
  public static final long MAX_AZURE_BLOCK_SIZE = 512 * 1024 * 1024L;
  public static final String AZURE_BLOCK_LOCATION_HOST_DEFAULT = "localhost";

  private FileSystemConfigurations() {}
}