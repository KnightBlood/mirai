package net.mamoe.mirai.qqandroid.network.protocol.packet.login

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.readBytes
import kotlinx.serialization.protobuf.ProtoBuf
import net.mamoe.mirai.data.Packet
import net.mamoe.mirai.qqandroid.QQAndroidBot
import net.mamoe.mirai.qqandroid.network.QQAndroidClient
import net.mamoe.mirai.qqandroid.network.io.buildJcePacket
import net.mamoe.mirai.qqandroid.network.protocol.jce.RequestPacket
import net.mamoe.mirai.qqandroid.network.protocol.jce.SvcReqRegister
import net.mamoe.mirai.qqandroid.network.protocol.jce.writeUniRequestPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.OutgoingPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.PacketFactory
import net.mamoe.mirai.qqandroid.network.protocol.packet.buildOutgingPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.oidb.oidb0x769.Oidb0x769
import net.mamoe.mirai.qqandroid.utils.NetworkType
import net.mamoe.mirai.utils.currentTimeSeconds
import net.mamoe.mirai.utils.io.encodeToString

@Suppress("EnumEntryName")
enum class RegPushReason {
    appRegister,
    createDefaultRegInfo,
    fillRegProxy,
    msfBoot,
    msfByNetChange,
    msfHeartTimeTooLong,
    serverPush,
    setOnlineStatus,
    unknown
}

internal object SvcReqRegisterPacket : PacketFactory<SvcReqRegisterPacket.Response>() {

    internal object Response : Packet

    operator fun invoke(
        client: QQAndroidClient,
        regPushReason: RegPushReason = RegPushReason.setOnlineStatus
    ): OutgoingPacket = buildOutgingPacket(client, key = client.wLoginSigInfo.wtSessionTicketKey) {
        writeUniRequestPacket(
            RequestPacket(
                sServantName = "PushService",
                sFuncName = "SvcReqRegister",
                sBuffer = buildJcePacket {
                    writeMap(
                        mapOf(
                            "SvcReqRegister" to buildJcePacket {
                                writeJceStruct(
                                    SvcReqRegister(
                                        cConnType = 0,
                                        lBid = 1 or 2 or 4,
                                        lUin = client.uin,
                                        iStatus = client.onlineStatus.id,
                                        bKikPC = 0, // 是否把 PC 踢下线
                                        bKikWeak = 0,
                                        timeStamp = currentTimeSeconds, // millis or seconds??
                                        iLargeSeq = 0,
                                        bRegType =
                                        if (regPushReason == RegPushReason.appRegister ||
                                            regPushReason == RegPushReason.fillRegProxy ||
                                            regPushReason == RegPushReason.createDefaultRegInfo ||
                                            regPushReason == RegPushReason.setOnlineStatus
                                        ) {
                                            0
                                        } else {
                                            1
                                        }.toByte(),
                                        bIsSetStatus = if (regPushReason == RegPushReason.setOnlineStatus) 1 else 0,
                                        iOSVersion = client.device.version.sdk.toLong(),
                                        cNetType = if (client.networkType == NetworkType.WIFI) 1 else 0,
                                        vecGuid = client.device.guid,
                                        strDevName = client.device.model.encodeToString(),
                                        strDevType = client.device.model.encodeToString(),
                                        strOSVer = client.device.version.release.encodeToString(),

                                        // register 时还需要
                                        /*
                                        var44.uNewSSOIp = field_127445;
                                        var44.uOldSSOIp = field_127444;
                                        var44.strVendorName = ROMUtil.getRomName();
                                        var44.strVendorOSName = ROMUtil.getRomVersion(20);
                                        */
                                        bytes_0x769_reqbody = ProtoBuf.dump(
                                            Oidb0x769.RequestBody.serializer(), Oidb0x769.RequestBody(
                                                rpt_config_list = listOf(
                                                    Oidb0x769.ConfigSeq(
                                                        type = 46,
                                                        version = 4
                                                    ),
                                                    Oidb0x769.ConfigSeq(
                                                        type = 283,
                                                        version = 0
                                                    )
                                                )
                                            )
                                        ),
                                        bSetMute = 0
                                    ), 0
                                )
                            }.readBytes()
                        ), 0
                    )
                }.readBytes()
            )
        )
    }

    override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): Response {
        return Response
    }
}