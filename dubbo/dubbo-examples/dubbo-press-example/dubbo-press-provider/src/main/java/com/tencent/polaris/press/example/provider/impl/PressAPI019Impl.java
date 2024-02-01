package com.tencent.polaris.press.example.provider.impl;

import com.tencent.polaris.press.example.provider.api.PressAPI012;
import com.tencent.polaris.press.example.provider.api.PressAPI019;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(version = "1.0.0")
public class PressAPI019Impl implements PressAPI019 {
    @Override
    public String sayHello(String name) {
        return "PressAPI001Impl " + name;
    }

    @Override
    public String sayHi(String name) {
        return "PressAPI001Impl " + name;
    }
}
