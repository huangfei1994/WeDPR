/*
 * Copyright 2017-2025  [webank-wedpr]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.webank.wedpr.core.config;

import com.webank.wedpr.core.utils.PropertiesHelper;
import java.util.Properties;

public class WeDPRConfig {
    private static Properties config = new Properties();

    public static Properties getConfig() {
        return config;
    }

    public static void setConfig(Properties properties) {
        config = properties;
    }

    public static <T> void set(String key, T value) {
        config.setProperty(key, String.valueOf(value));
    }

    public static <T> T apply(String key, T defaultValue, boolean required) {
        return PropertiesHelper.getValue(config, key, required, defaultValue);
    }

    public static <T> T apply(String key, T defaultValue) {
        return PropertiesHelper.getValue(config, key, false, defaultValue);
    }
}