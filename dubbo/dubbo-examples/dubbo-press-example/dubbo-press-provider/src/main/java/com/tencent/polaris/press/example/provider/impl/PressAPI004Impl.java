package com.tencent.polaris.press.example.provider.impl;

import com.tencent.polaris.press.example.provider.api.PressAPI003;
import com.tencent.polaris.press.example.provider.api.PressAPI004;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(version = "1.0.0")
public class PressAPI004Impl implements PressAPI004 {
    @Override
    public String sayHello(String name) {
        return "PressAPI001Impl " + name;
    }

    @Override
    public String sayHi(String name) {
        return "PressAPI001Impl " + name;
    }
}
