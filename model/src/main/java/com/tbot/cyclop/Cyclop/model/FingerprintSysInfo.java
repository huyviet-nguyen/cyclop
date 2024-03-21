package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FingerprintSysInfo {
    private String id;
    private String mtoken;
    private String mhash;
    private String sys;
    private String sysVer;
    private String browserName;
    private String browserVer;
    private String kernelName;
    private String kernelVer;
    private String gpuType;
    private String language;
    private String displayResolution;
    private String colorDepth;
    private String totalMemory;
    private String pixelRatio;
    private String timeZone;
    private String sessionEnable;
    private String storageEnable;
    private String indexedDbEnable;
    private String webSqlEnable;
    private String doNotTrack;
    private String isAlphaGo;
    private String canvasCrc;
    private String fonts;
    private String eDevices;
    private String audioHash;
    private String webglHash;
    private String memberId;
    private String envInfo;
    private String hostname;
    private String sdkVersion;
    private int productType;
    private int platformType;
}
