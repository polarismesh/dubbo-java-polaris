# dubbo-java-polaris

[![codecov](https://codecov.io/gh/polarismesh/dubbo-java-polaris/branch/dubbo-3.2.x/graph/badge.svg?token=I9fctxnRWi)](https://app.codecov.io/gh/polarismesh/dubbo-java-polaris/tree/dubbo-3.2.x)
[![Testing](https://github.com/polarismesh/dubbo-java-polaris/actions/workflows/testing.yml/badge.svg?branch=dubbo-3.2.x)](https://github.com/polarismesh/dubbo-java-polaris/actions/workflows/testing.yml)

## 介绍

dubbo-java-polaris 是 [Apache Dubbo](https://github.com/apache/dubbo) 框架的扩展，便于使用dubbo框架开发的应用可以接入并使用北极星的各部分功能。

当前支持版本到 3.2.x、2.7.x。

## 插件功能说明

### 服务注册发现 

实现dubbo服务往北极星上进行注册，以及服务调用时从北极星拉取服务实例的功能。相关插件：

- Apache Dubbo：dubbo-registry-polaris

### 元数据上报（目前仅3.2.x版本支持）

实验dubbo服务的元数据（服务、接口信息）上报到北极星，以及服务消费方拉取服务提供方的元数据信息。相关插件：

- Apache Dubbo：dubbo-metadatareport-polaris
