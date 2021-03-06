/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <sekai@neko.services>                    *
 * Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.database

import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import cn.hutool.core.lang.Validator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.chain.ChainBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http.toUri
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.methodsV2fly
import io.nekohasekai.sagernet.fmt.shadowsocks.toUri
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.toUri
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.toUri
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan.toUri
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.trojan_go.toUri
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.toUri
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ui.profile.*

@Entity(
    tableName = "proxy_entities", indices = [
        Index("groupId", name = "groupId")
    ]
)
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var groupId: Long,
    var type: Int = 0,
    var userOrder: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
    var socksBean: SOCKSBean? = null,
    var httpBean: HttpBean? = null,
    var ssBean: ShadowsocksBean? = null,
    var ssrBean: ShadowsocksRBean? = null,
    var vmessBean: VMessBean? = null,
    var vlessBean: VLESSBean? = null,
    var trojanBean: TrojanBean? = null,
    var trojanGoBean: TrojanGoBean? = null,
    var chainBean: ChainBean? = null,
) : Parcelable {

    @Ignore
    @Transient
    var dirty: Boolean = false

    @Ignore
    @Transient
    var stats: TrafficStats? = null

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong()
    ) {
        dirty = parcel.readByte() > 0
        val byteArray = ByteArray(parcel.readInt())
        parcel.readByteArray(byteArray)
        when (type) {
            0 -> socksBean = KryoConverters.socksDeserialize(byteArray)
            1 -> httpBean = KryoConverters.httpDeserialize(byteArray)
            2 -> ssBean = KryoConverters.shadowsocksDeserialize(byteArray)
            3 -> ssrBean = KryoConverters.shadowsocksRDeserialize(byteArray)
            4 -> vmessBean = KryoConverters.vmessDeserialize(byteArray)
            5 -> vlessBean = KryoConverters.vlessDeserialize(byteArray)
            6 -> trojanBean = KryoConverters.trojanDeserialize(byteArray)
            7 -> trojanGoBean = KryoConverters.trojanGoDeserialize(byteArray)
            8 -> chainBean = KryoConverters.chainDeserialize(byteArray)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(groupId)
        parcel.writeInt(type)
        parcel.writeLong(userOrder)
        parcel.writeLong(tx)
        parcel.writeLong(rx)
        parcel.writeByte(if (dirty) 1 else 0)
        val byteArray = KryoConverters.serialize(requireBean())
        parcel.writeInt(byteArray.size)
        parcel.writeByteArray(byteArray)
    }

    companion object {
        val chainName by lazy { app.getString(R.string.proxy_chain) }
    }

    fun displayType(): String {
        return when (type) {
            0 -> "SOCKS5"
            1 -> if (requireHttp().tls) "HTTPS" else "HTTP"
            2 -> "Shadowsocks"
            3 -> "ShadowsocksR"
            4 -> "VMess"
            5 -> "VLESS"
            6 -> "Trojan"
            7 -> "Trojan-Go"
            8 -> chainName
            else -> "Undefined type $type"
        }
    }

    fun displayName(): String {
        return requireBean().displayName()
    }

    fun urlFixed(): String {
        val bean = requireBean()
        if (bean is ChainBean) {
            if (bean.proxies.isNotEmpty()) {
                val firstEntity = ProfileManager.getProfile(bean.proxies[0])
                if (firstEntity != null) {
                    return firstEntity.urlFixed();
                }
            }
        }
        return if (Validator.isIpv6(bean.serverAddress)) {
            "[${bean.serverAddress}]:${bean.serverPort}"
        } else {
            "${bean.serverAddress}:${bean.serverPort}"
        }
    }

    fun requireBean(): AbstractBean {
        return when (type) {
            // 2 -> vmessBean ?: error("Null vmess node")
            0 -> socksBean ?: error("Null socks node")
            1 -> httpBean ?: error("Null http node")
            2 -> ssBean ?: error("Null ss node")
            3 -> ssrBean ?: error("Null ssr node")
            4 -> vmessBean ?: error("Null vmess node")
            5 -> vlessBean ?: error("Null vless node")
            6 -> trojanBean ?: error("Null trojan node")
            7 -> trojanGoBean ?: error("Null trojan-go node")
            8 -> chainBean ?: error("Null chain bean")
            else -> error("Undefined type $type")
        }
    }

    fun toUri(): String? {
        return when (type) {
            0 -> requireSOCKS().toUri()
            1 -> requireHttp().toUri()
            2 -> requireSS().toUri()
            3 -> requireSSR().toUri()
            4 -> requireVMess().toUri(true)
            5 -> requireVLESS().toUri(true)
            6 -> requireTrojan().toUri()
            7 -> requireTrojanGo().toUri()
            else -> null
        }
    }

    fun needExternal(): Boolean {
        return when (type) {
            0 -> false
            1 -> false
            2 -> useExternalShadowsocks()
            3 -> true
            4 -> false
            5 -> useXray()
            6 -> useXray()
            7 -> true
            8 -> false
            else -> error("Undefined type $type")
        }
    }

    fun isV2RayNetworkTcp(): Boolean {
        val bean = requireBean() as StandardV2RayBean
        return when (bean.type) {
            "tcp", "ws", "http" -> true
            else -> false
        }
    }

    fun needCoreMux(): Boolean {
        val enableMuxForAll by lazy { DataStore.enableMuxForAll }
        return when (type) {
            0 -> enableMuxForAll
            1 -> enableMuxForAll
            2 -> enableMuxForAll
            3 -> enableMuxForAll
            4 -> isV2RayNetworkTcp()
            5 -> !useXray()
            6 -> enableMuxForAll && !useXray()
            7 -> false
            else -> error("Undefined type $type")
        }
    }

    fun needXrayMux(): Boolean {
        val enableMuxForAll by lazy { DataStore.enableMuxForAll }
        return when (type) {
            5 -> isV2RayNetworkTcp()
            6 -> enableMuxForAll
            else -> error("Undefined type $type")
        }
    }

    fun useExternalShadowsocks(): Boolean {
        if (type != 2) return false
        if (DataStore.forceShadowsocksRust) return true
        val bean = requireSS()
        if (bean.plugin.isNotBlank()) {
            Logs.d("Requiring plugin ${bean.plugin}")
            return true
        }
        if (bean.method !in methodsV2fly) return true
        return false
    }

    fun useXray(): Boolean {
        when (val bean = requireBean()) {
            is VLESSBean -> {
                if (bean.security != "xtls") return false
                if (bean.type != "tcp") return false
                if (bean.headerType.isNotBlank() && bean.headerType != "none") return false
                return true
            }
            is TrojanBean -> {
                if (bean.security == "xtls") return true
            }
        }

        return false
    }

    fun putBean(bean: AbstractBean) {
        when (bean) {
            is SOCKSBean -> {
                type = 0
                socksBean = bean
            }
            is HttpBean -> {
                type = 1
                httpBean = bean
            }
            is ShadowsocksBean -> {
                type = 2
                ssBean = bean
            }
            is ShadowsocksRBean -> {
                type = 3
                ssrBean = bean
            }
            is VMessBean -> {
                type = 4
                vmessBean = bean
            }
            is VLESSBean -> {
                type = 5
                vlessBean = bean
            }
            is TrojanBean -> {
                type = 6
                trojanBean = bean
            }
            is TrojanGoBean -> {
                type = 7
                trojanGoBean = bean
            }
            is ChainBean -> {
                type = 8
                chainBean = bean
            }
            else -> error("Undefined type $type")
        }
    }

    fun requireSOCKS() = requireBean() as SOCKSBean
    fun requireSS() = requireBean() as ShadowsocksBean
    fun requireSSR() = requireBean() as ShadowsocksRBean
    fun requireVMess() = requireBean() as VMessBean
    fun requireVLESS() = requireBean() as VLESSBean
    fun requireTrojan() = requireBean() as TrojanBean
    fun requireHttp() = requireBean() as HttpBean
    fun requireTrojanGo() = requireBean() as TrojanGoBean
    fun requireChain() = requireBean() as ChainBean

    fun settingIntent(ctx: Context, isSubscription: Boolean): Intent {
        return Intent(
            ctx, when (type) {
                0 -> SocksSettingsActivity::class.java
                1 -> HttpSettingsActivity::class.java
                2 -> ShadowsocksSettingsActivity::class.java
                3 -> ShadowsocksRSettingsActivity::class.java
                4 -> VMessSettingsActivity::class.java
                5 -> VLESSSettingsActivity::class.java
                6 -> TrojanSettingsActivity::class.java
                7 -> TrojanGoSettingsActivity::class.java
                8 -> ChainSettingsActivity::class.java
                else -> throw IllegalArgumentException()
            }
        ).apply {
            putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, id)
            putExtra(ProfileSettingsActivity.EXTRA_IS_SUBSCRIPTION, isSubscription)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT id FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getIdsByGroup(groupId: Long): List<Long>

        @Query("SELECT * FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getByGroup(groupId: Long): List<ProxyEntity>

        @Query("SELECT * FROM proxy_entities WHERE id in (:proxyIds)")
        fun getEntities(proxyIds: List<Long>): List<ProxyEntity>

        @Query("SELECT COUNT(*) FROM proxy_entities WHERE groupId = :groupId")
        fun countByGroup(groupId: Long): Long

        @Query("SELECT  MAX(userOrder) + 1 FROM proxy_entities WHERE groupId = :groupId")
        fun nextOrder(groupId: Long): Long?

        @Query("SELECT * FROM proxy_entities WHERE id = :proxyId")
        fun getById(proxyId: Long): ProxyEntity?

        @Query("DELETE FROM proxy_entities WHERE id IN (:proxyId)")
        fun deleteById(proxyId: Long): Int

        @Query("DELETE FROM proxy_entities WHERE groupId in (:groupId)")
        fun deleteByGroup(vararg groupId: Long)

        @Delete
        fun deleteProxy(vararg proxy: ProxyEntity): Int

        @Update
        fun updateProxy(vararg proxy: ProxyEntity): Int

        @Insert
        fun addProxy(proxy: ProxyEntity): Long

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteAll(groupId: Long): Int

    }

    override fun describeContents(): Int {
        return 0
    }

    object CREATOR : Parcelable.Creator<ProxyEntity> {
        override fun createFromParcel(parcel: Parcel): ProxyEntity {
            return ProxyEntity(parcel)
        }

        override fun newArray(size: Int): Array<ProxyEntity?> {
            return arrayOfNulls(size)
        }
    }
}