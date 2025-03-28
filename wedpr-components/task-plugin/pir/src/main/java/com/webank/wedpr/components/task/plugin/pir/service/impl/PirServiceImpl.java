/*
 * Copyright 2017-2025  [webank-wedpr]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.webank.wedpr.components.task.plugin.pir.service.impl;

import com.webank.wedpr.common.config.WeDPRCommonConfig;
import com.webank.wedpr.common.utils.*;
import com.webank.wedpr.components.api.credential.core.CredentialVerifier;
import com.webank.wedpr.components.api.credential.core.impl.CredentialVerifierImpl;
import com.webank.wedpr.components.db.mapper.dataset.mapper.DatasetMapper;
import com.webank.wedpr.components.db.mapper.service.publish.dao.*;
import com.webank.wedpr.components.db.mapper.service.publish.model.PirServiceSetting;
import com.webank.wedpr.components.db.mapper.service.publish.model.ServiceStatus;
import com.webank.wedpr.components.db.mapper.service.publish.verifier.ServiceAuthVerifier;
import com.webank.wedpr.components.db.mapper.service.publish.verifier.impl.ServiceAuthVerifierImpl;
import com.webank.wedpr.components.hook.ServiceHook;
import com.webank.wedpr.components.pir.sdk.config.PirSDKConfig;
import com.webank.wedpr.components.pir.sdk.core.ObfuscateData;
import com.webank.wedpr.components.pir.sdk.core.ObfuscateQueryResult;
import com.webank.wedpr.components.pir.sdk.core.OtResult;
import com.webank.wedpr.components.pir.sdk.model.PirQueryParam;
import com.webank.wedpr.components.pir.sdk.model.PirQueryRequest;
import com.webank.wedpr.components.storage.api.FileStorageInterface;
import com.webank.wedpr.components.storage.config.HdfsStorageConfig;
import com.webank.wedpr.components.storage.config.LocalStorageConfig;
import com.webank.wedpr.components.task.plugin.pir.core.Obfuscator;
import com.webank.wedpr.components.task.plugin.pir.core.PirDatasetConstructor;
import com.webank.wedpr.components.task.plugin.pir.core.impl.ObfuscatorImpl;
import com.webank.wedpr.components.task.plugin.pir.core.impl.PirDatasetConstructorImpl;
import com.webank.wedpr.components.task.plugin.pir.dao.NativeSQLMapperWrapper;
import com.webank.wedpr.components.task.plugin.pir.handler.PirServiceHook;
import com.webank.wedpr.components.task.plugin.pir.model.ObfuscationParam;
import com.webank.wedpr.components.task.plugin.pir.model.PirDataItem;
import com.webank.wedpr.components.task.plugin.pir.service.PirService;
import com.webank.wedpr.components.task.plugin.pir.transport.PirTopicSubscriber;
import com.webank.wedpr.components.task.plugin.pir.transport.impl.PirTopicSubscriberImpl;
import com.webank.wedpr.sdk.jni.transport.TransportConfig;
import com.webank.wedpr.sdk.jni.transport.WeDPRTransport;
import java.util.List;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PirServiceImpl implements PirService {
    private static final Logger logger = LoggerFactory.getLogger(PirServiceImpl.class);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatasetMapper datasetMapper;
    @Autowired private HdfsStorageConfig hdfsConfig;
    @Autowired private LocalStorageConfig localStorageConfig;

    @Qualifier("fileStorage")
    @Autowired
    private FileStorageInterface fileStorage;

    @Qualifier("weDPRTransport")
    @Autowired
    private WeDPRTransport weDPRTransport;

    @Qualifier("serviceHook")
    @Autowired
    private ServiceHook serviceHook;

    private CredentialVerifier verifier;

    @Autowired private PublishedServiceMapper publishedServiceMapper;
    @Autowired private ServiceInvokeMapper serviceInvokeMapper;
    @Autowired private ServiceAuthMapper serviceAuthMapper;

    private final ThreadPoolService threadPoolService = PirSDKConfig.getThreadPoolService();

    private NativeSQLMapperWrapper nativeSQLMapperWrapper;
    private Obfuscator obfuscator;
    private PirDatasetConstructor pirDatasetConstructor;
    private PirTopicSubscriber pirTopicSubscriber;
    private PirServiceHook pirServiceHook;
    private ServiceAuthVerifier serviceAuthVerifier;

    @PostConstruct
    public void init() throws Exception {
        this.obfuscator = new ObfuscatorImpl();
        this.nativeSQLMapperWrapper = new NativeSQLMapperWrapper(jdbcTemplate);
        this.pirDatasetConstructor =
                new PirDatasetConstructorImpl(datasetMapper, fileStorage, jdbcTemplate);
        this.pirServiceHook = new PirServiceHook(serviceHook, serviceInvokeMapper);
        this.pirTopicSubscriber =
                new PirTopicSubscriberImpl(
                        weDPRTransport, new CredentialVerifierImpl(null), pirServiceHook);
        // get the access entrypoint
        TransportConfig transportConfig = weDPRTransport.getTransportConfig();
        String accessEntryPoint =
                Common.getUrl(
                        transportConfig.getSelfEndPoint().getHostIP()
                                + ":"
                                + WeDPRCommonConfig.getServerListenPort());
        this.weDPRTransport.registerComponent(WeDPRCommonConfig.getWedprZone());
        this.weDPRTransport.registerService(
                Common.getServiceName(WeDPRCommonConfig.getAgency(), Constant.PIR_SERVICE_TYPE),
                accessEntryPoint);
        logger.info(
                "PirServiceImpl, register service, accessEntryPoint: {}, wedprZone: {}",
                accessEntryPoint,
                WeDPRCommonConfig.getWedprZone());
        registerPublishedServices();
        this.serviceAuthVerifier = new ServiceAuthVerifierImpl(serviceAuthMapper);
    }

    protected void registerPublishedServices() throws Exception {
        logger.info("registerPublishedService");
        PublishedServiceInfo condition = new PublishedServiceInfo("");
        condition.setAgency(WeDPRCommonConfig.getAgency());
        condition.setStatus(ServiceStatus.PublishSuccess.getStatus());
        List<PublishedServiceInfo> serviceInfoList =
                publishedServiceMapper.queryPublishedService(condition, null);
        if (serviceInfoList == null || serviceInfoList.isEmpty()) {
            return;
        }
        for (PublishedServiceInfo serviceInfo : serviceInfoList) {
            logger.info("registerPublishedService for {}", serviceInfo.getServiceId());
            registerPublishedService(serviceInfo.getServiceId());
            logger.info("registerPublishedService for {} success", serviceInfo.getServiceId());
        }
        logger.info("registerPublishedService success");
    }

    private void registerPublishedService(String serviceId) throws Exception {
        pirTopicSubscriber.registerService(
                serviceId,
                new PirTopicSubscriber.QueryHandler() {
                    @Override
                    public WeDPRResponse onQuery(PirQueryRequest pirQueryRequest) throws Exception {
                        return query(pirQueryRequest);
                    }
                });
    }

    @Override
    public WeDPRResponse query(PirQueryRequest pirQueryRequest) throws Exception {
        // check the request
        pirQueryRequest.check(false);
        PublishedServiceInfo condition =
                new PublishedServiceInfo(pirQueryRequest.getQueryParam().getServiceId());
        // check the service
        List<PublishedServiceInfo> result =
                this.publishedServiceMapper.queryPublishedService(condition, null);
        if (result == null || result.isEmpty()) {
            throw new WeDPRException(
                    "The service "
                            + pirQueryRequest.getQueryParam().getServiceId()
                            + " not exists!");
        }
        // check the service status
        if (result.get(0).getServiceStatus() == null
                || !result.get(0).getServiceStatus().isReady()) {
            throw new WeDPRException(
                    "The service "
                            + pirQueryRequest.getQueryParam().getServiceId()
                            + " is not ready yet, status: "
                            + result.get(0).getStatus());
        }
        // get the serviceSetting
        PirServiceSetting serviceSetting =
                PirServiceSetting.deserialize(result.get(0).getServiceConfig());
        // check searchType
        pirQueryRequest.checkSearchType(result.get(0).getServiceId(), serviceSetting);
        // check the auth
        serviceAuthVerifier.verify(
                pirQueryRequest.getQueryParam().getServiceId(),
                pirQueryRequest.getQueryParam().getCredentialInfo());

        return query(
                pirQueryRequest.getQueryParam(),
                serviceSetting,
                pirQueryRequest.getObfuscateData());
    }

    /**
     * query the data
     *
     * @param obfuscateData the query parm
     * @return the result
     */
    protected WeDPRResponse query(
            PirQueryParam pirQueryParam,
            PirServiceSetting serviceSetting,
            ObfuscateData obfuscateData) {
        try {
            ObfuscationParam obfuscationParam =
                    new ObfuscationParam(obfuscateData, pirQueryParam.getAlgorithmType());
            ObfuscateQueryResult obfuscateQueryResult =
                    new ObfuscateQueryResult(
                            serviceSetting.getDatasetId(),
                            pirQueryParam.getAlgorithmType().toString());
            for (ObfuscateData.ObfuscateDataItem dataItem : obfuscateData.getObfuscateDataItems()) {
                List<PirDataItem> queriedResult =
                        this.nativeSQLMapperWrapper.query(serviceSetting, pirQueryParam, dataItem);
                // without recorder
                if (queriedResult == null || queriedResult.isEmpty()) {
                    obfuscateQueryResult.getOtResultList().add(new OtResult());
                    continue;
                }
                obfuscationParam.setIndex(dataItem.getIdIndex());
                List<OtResult.OtResultItem> otResultItems =
                        this.obfuscator.obfuscate(obfuscationParam, queriedResult, dataItem);
                obfuscateQueryResult.getOtResultList().add(new OtResult(otResultItems));
            }
            WeDPRResponse response =
                    new WeDPRResponse(Constant.WEDPR_SUCCESS, Constant.WEDPR_SUCCESS_MSG);
            response.setData(obfuscateQueryResult);
            return response;
        } catch (Exception e) {
            logger.warn(
                    "query exception, dataset: {}, queryParam: {}, e: ",
                    serviceSetting.getDatasetId(),
                    pirQueryParam.toString(),
                    e);
            return new WeDPRResponse(
                    Constant.WEDPR_FAILED,
                    "Pir query failed for "
                            + e.getMessage()
                            + ", datasetID: "
                            + serviceSetting.getDatasetId());
        }
    }

    protected void onPublishFinished(String serviceID, ServiceStatus serviceStatus, String msg) {
        PublishedServiceInfo updatedInfo = new PublishedServiceInfo(serviceID);
        updatedInfo.setStatus(serviceStatus.getStatus());
        updatedInfo.setStatusMsg(msg);
        publishedServiceMapper.updateServiceInfo(updatedInfo);
    }

    /**
     * publish pir service
     *
     * @param serviceSetting the serviceSetting
     * @return the result
     */
    @Override
    public WeDPRResponse publish(String serviceID, PirServiceSetting serviceSetting) {
        try {
            logger.info(
                    "Publish dataset: {}, serviceID: {}", serviceSetting.getDatasetId(), serviceID);
            serviceSetting.check();
            // Note: the publish operation maybe time-consuming, async here
            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        pirDatasetConstructor.construct(serviceSetting);
                                        registerPublishedService(serviceID);
                                        onPublishFinished(
                                                serviceID,
                                                ServiceStatus.PublishSuccess,
                                                Constant.WEDPR_SUCCESS_MSG);
                                        logger.info(
                                                "Publish dataset: {} success, serviceId: {}",
                                                serviceSetting.getDatasetId(),
                                                serviceID);
                                    } catch (Exception e) {
                                        logger.warn(
                                                "Publish failed, serviceId: {}, setting: {}, e: ",
                                                serviceID,
                                                serviceSetting.toString(),
                                                e);
                                        onPublishFinished(
                                                serviceID,
                                                ServiceStatus.PublishFailed,
                                                "Publish PIR service "
                                                        + serviceID
                                                        + " failed for "
                                                        + e.getMessage());
                                    }
                                }
                            });

            return new WeDPRResponse(Constant.WEDPR_SUCCESS, Constant.WEDPR_SUCCESS_MSG);
        } catch (Exception e) {
            onPublishFinished(
                    serviceID,
                    ServiceStatus.PublishFailed,
                    "Publish PIR service " + serviceID + " failed for " + e.getMessage());
            logger.warn(
                    "publish dataset {} failed, serviceID: {}, error: ",
                    serviceSetting.getDatasetId(),
                    serviceID,
                    e);
            return new WeDPRResponse(
                    Constant.WEDPR_FAILED, "Publish dataset {} failed for " + e.getMessage());
        }
    }
}
