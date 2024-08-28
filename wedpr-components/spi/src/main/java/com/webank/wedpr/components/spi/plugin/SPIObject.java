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
package com.webank.wedpr.components.spi.plugin;

public class SPIObject implements Comparable<SPIObject> {
    private SPIInfo spiInfo;

    public SPIObject(SPIInfo spiInfo) {
        this.spiInfo = spiInfo;
    }

    public SPIObject() {}

    public SPIInfo getSpiInfo() {
        return spiInfo;
    }

    public void setSpiInfo(SPIInfo spiInfo) {
        this.spiInfo = spiInfo;
    }

    @Override
    public int compareTo(SPIObject o) {
        return spiInfo.getPriority().compareTo(o.getSpiInfo().getPriority());
    }
}