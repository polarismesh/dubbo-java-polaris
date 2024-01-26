# dubbo-java-polaris

## 介绍

dubbo-java-polaris 是dubbo框架的扩展，便于使用dubbo框架开发的应用可以接入并使用北极星的各部分功能，

支持 dubbo3 版本的适配：

- [Apache Dubbo](https://github.com/apache/dubbo)，当前支持版本到 3.2.x

## 插件功能说明

### 服务注册发现 

实现dubbo服务往北极星上进行注册，以及服务调用时从北极星拉取服务实例的功能。相关插件：

- Apache Dubbo：dubbo-registry-polaris

### 元数据中心

实现dubbo接口元数据信息上报至北极星的服务契约中心。相关插件：

- Apache Dubbo：dubbo-metadatareport-polaris

### 配置中心

实现dubbo配置信息数据信息上报至北极星的配置中心，以及支持从北极星配置中心进行拉取。相关插件：

- Apache Dubbo：dubbo-configcenter-polaris

### 动态路由

实现按照请求头、方法等参数，对请求进行按版本、标签的调度。相关插件：

- Apache Dubbo：dubbo-router-polaris

### 访问限流

实现按照请求头、方法等参数，对流量进行限频。相关插件：

- Apache Dubbo：dubbo-ratelimit-polaris

### 熔断降级

实现按请求调用的回包统计（连续错误数、错误率等）指标，对故障节点、接口、服务进行隔离和熔断。相关插件：

- Apache Dubbo：dubbo-circuitbreaker-polaris
