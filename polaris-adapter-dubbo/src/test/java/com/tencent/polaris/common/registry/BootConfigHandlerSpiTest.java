/*
 * Tencent is pleased to support the open source community by making dubbo-polaris-java available.
 *
 * Copyright (C) 2021 Tencent. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.tencent.polaris.common.registry;

import com.tencent.polaris.common.config.BaseBootConfigHandler;
import com.tencent.polaris.common.config.BootConfigHandler;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

public class BootConfigHandlerSpiTest {

    @Test
    public void baseHandlerLoadedViaDubboSpi() {
        ExtensionLoader<BootConfigHandler> loader =
                ApplicationModel.defaultModel().getExtensionLoader(BootConfigHandler.class);
        Set<String> names = loader.getSupportedExtensions();
        Assert.assertTrue("expect baseBootConfigHandler in SPI extensions, got " + names,
                names.contains("baseBootConfigHandler"));
        BootConfigHandler instance = loader.getExtension("baseBootConfigHandler");
        Assert.assertTrue(instance instanceof BaseBootConfigHandler);
    }
}
