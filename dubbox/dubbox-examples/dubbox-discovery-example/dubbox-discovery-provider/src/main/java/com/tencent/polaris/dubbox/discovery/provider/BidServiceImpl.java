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

package com.tencent.polaris.dubbox.discovery.provider;

import com.tencent.polaris.dubbox.discovery.example.api.BidRequest;
import com.tencent.polaris.dubbox.discovery.example.api.BidResponse;
import com.tencent.polaris.dubbox.discovery.example.api.BidService;
import com.tencent.polaris.dubbox.discovery.example.api.SeatBid;
import java.util.ArrayList;
import java.util.List;

public class BidServiceImpl implements BidService {

    public BidResponse bid(BidRequest request) {
        BidResponse response = new BidResponse();

        response.setId("abc");

        SeatBid seatBid = new SeatBid();
        seatBid.setGroup("group");
        seatBid.setSeat("seat");
        List<SeatBid> seatBids = new ArrayList<SeatBid>(1);
        seatBids.add(seatBid);

        response.setSeatBids(seatBids);

        return response;
    }

    public void throwNPE() throws NullPointerException {
        throw new NullPointerException();
    }
}
