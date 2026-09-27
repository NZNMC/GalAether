package com.galstruo.app.data.network

import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 全局手动代理配置(设置页修改后立即生效)。
 * 所有网络客户端在发起新请求前检查配置版本,变了就重建客户端,
 * 进行中的请求不受影响(下载中断后重试即走新代理)。
 */
object NetConfig {

    var enabled = false
        private set
    var host = ""
        private set
    var port = 7890
        private set

    /** 配置版本号,网络客户端据此判断是否需要重建 */
    var version = 0
        private set

    fun update(enabled: Boolean, host: String, port: Int) {
        this.enabled = enabled
        this.host = host.trim()
        this.port = port
        version++
    }

    fun apply(builder: OkHttpClient.Builder) {
        if (enabled && host.isNotBlank() && port in 1..65535) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
        }
    }
}
