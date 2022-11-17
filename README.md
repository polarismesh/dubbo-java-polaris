# dubbo-java-polaris

## 介绍

dubbo-java-polaris 是dubbo框架的扩展，便于使用dubbo框架开发的应用可以接入并使用北极星的各部分功能，

支持以下2个dubbo版本的适配：

- [Apache Dubbo](https://github.com/apache/dubbo)，当前支持版本到2.7.18
- [DubboX](https://github.com/dangdangdotcom/dubbox)，当前支持版本到2.8.4

## 插件功能说明

### 服务注册发现 

实现dubbo服务往北极星上进行注册，以及服务调用时从北极星拉取服务实例的功能。相关插件：

- dubbo-registry-polaris
- dubbox-registry-polaris

### 动态路由

实现按照请求头、方法等参数，对请求进行按版本、标签的调度。相关插件：

- dubbo-router-polaris
- dubbox-router-polaris

### 访问限流

实现按照请求头、方法等参数，对流量进行限频。相关插件：

- dubbo-ratelmit-polaris
- dubbox-ratelimit-polaris

## 使用指南

- 服务注册使用指南：[服务注册](https://polarismesh.cn/docs/%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97/java%E5%BA%94%E7%94%A8%E5%BC%80%E5%8F%91/dubbo/%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C/)
- 服务发现使用指南：[服务发现](https://polarismesh.cn/docs/%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97/java%E5%BA%94%E7%94%A8%E5%BC%80%E5%8F%91/dubbo/%E6%9C%8D%E5%8A%A1%E5%8F%91%E7%8E%B0/)
- 动态路由使用指南：[动态路由](https://polarismesh.cn/docs/%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97/java%E5%BA%94%E7%94%A8%E5%BC%80%E5%8F%91/dubbo/%E5%8A%A8%E6%80%81%E8%B7%AF%E7%94%B1/)
- 访问限流使用指南：[访问限流](https://polarismesh.cn/docs/%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97/java%E5%BA%94%E7%94%A8%E5%BC%80%E5%8F%91/dubbo/%E8%AE%BF%E9%97%AE%E9%99%90%E6%B5%81/)