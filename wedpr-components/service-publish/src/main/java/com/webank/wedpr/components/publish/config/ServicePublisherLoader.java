package com.webank.wedpr.components.publish.config;

import com.webank.wedpr.common.config.WeDPRCommonConfig;
import com.webank.wedpr.common.utils.WeDPRResponseFactory;
import com.webank.wedpr.components.db.mapper.service.publish.dao.PublishedServiceMapper;
import com.webank.wedpr.components.db.mapper.service.publish.dao.ServiceAuthMapper;
import com.webank.wedpr.components.hook.ServiceHook;
import com.webank.wedpr.components.loadbalancer.LoadBalancer;
import com.webank.wedpr.components.publish.helper.PublishServiceHelper;
import com.webank.wedpr.components.publish.hook.ServicePublishCallback;
import com.webank.wedpr.components.publish.sync.PublishSyncer;
import com.webank.wedpr.components.publish.sync.PublishSyncerCommitHandler;
import com.webank.wedpr.components.publish.sync.api.PublishSyncerApi;
import com.webank.wedpr.components.sync.ResourceSyncer;
import com.webank.wedpr.components.sync.core.ResourceActionRecorderBuilder;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zachma
 * @since 2024/8/31
 */
@Configuration
public class ServicePublisherLoader {
    private static final Logger logger = LoggerFactory.getLogger(ServicePublisherLoader.class);

    @Autowired
    @Qualifier("resourceSyncer")
    private ResourceSyncer resourceSyncer;

    @Autowired
    @Qualifier("loadBalancer")
    private LoadBalancer loadBalancer;

    @Autowired
    @Qualifier("serviceHook")
    private ServiceHook serviceHook;

    @Autowired private ServiceAuthMapper serviceAuthMapper;
    @Autowired private PublishedServiceMapper publishedServiceMapper;

    @PostConstruct
    public void init() {
        logger.info("Register serviceCallback");

        ServicePublishCallback callback =
                new ServicePublishCallback(
                        loadBalancer, new WeDPRResponseFactory(), serviceAuthMapper);

        for (PublishServiceHelper.PublishType publishType :
                PublishServiceHelper.PublishType.values()) {
            serviceHook.registerServiceCallback(publishType.getType(), callback);
        }
        logger.info("Register serviceCallback success");
    }

    @Bean(name = "publishSyncer")
    public PublishSyncerApi newPublishSyncer() {

        PublishSyncer publishSyncer = new PublishSyncer();
        String agency = WeDPRCommonConfig.getAgency();
        logger.info(" => create publish syncer, agency: {}", agency);

        String resourceType = ResourceSyncer.ResourceType.Publish.getType();
        ResourceActionRecorderBuilder resourceBuilder =
                new ResourceActionRecorderBuilder(agency, resourceType);
        publishSyncer.setResourceSyncer(resourceSyncer);
        publishSyncer.setResourceBuilder(resourceBuilder);

        PublishSyncerCommitHandler publishSyncerCommitHandler =
                new PublishSyncerCommitHandler(publishedServiceMapper);
        resourceSyncer.registerCommitHandler(resourceType, publishSyncerCommitHandler);
        return publishSyncer;
    }
}
