/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.tencent.polaris.dubbox.discovery.consumer;

import com.alibaba.dubbo.config.ReferenceConfig;
import com.tencent.polaris.dubbox.discovery.example.api.BidRequest;
import com.tencent.polaris.dubbox.discovery.example.api.BidService;
import com.tencent.polaris.dubbox.discovery.example.api.Device;
import com.tencent.polaris.dubbox.discovery.example.api.Geo;
import com.tencent.polaris.dubbox.discovery.example.api.Impression;
import java.util.ArrayList;
import java.util.List;

public class DemoAction {

    private BidService bidService;

    public BidService getBidService() {
        return bidService;
    }

    public void setBidService(BidService bidService) {
        this.bidService = bidService;
    }

    public void start() {
        BidRequest request = new BidRequest();

        Impression imp = new Impression();
        imp.setBidFloor(1.1);
        imp.setId("abc");
        List<Impression> imps = new ArrayList<Impression>(1);
        imps.add(imp);
        request.setImpressions(imps);

        Geo geo = new Geo();
        geo.setCity("beijing");
        geo.setCountry("china");
        geo.setLat(100.1f);
        geo.setLon(100.1f);

        Device device = new Device();
        device.setMake("apple");
        device.setOs("ios");
        device.setVersion("7.0");
        device.setLang("zh_CN");
        device.setModel("iphone");
        device.setGeo(geo);
        request.setDevice(device);

        for (int i = 0; i < 10; i++) {
            try {
                System.out.println(bidService.bid(request).getId());
                System.out.println("SUCESS: got bid response id: " + bidService.bid(request).getId());
            } catch (Throwable e) {
                System.out.printf("exception caugh %s%n", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
    }

}
