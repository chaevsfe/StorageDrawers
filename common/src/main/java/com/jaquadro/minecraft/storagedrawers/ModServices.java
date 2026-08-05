package com.jaquadro.minecraft.storagedrawers;

import com.jaquadro.minecraft.storagedrawers.service.ResourceFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModServices
{
    public static final Logger log = LogManager.getLogger();

    private static final Set<String> reportedSites = ConcurrentHashMap.newKeySet();

    public static void reportOnce (String site, Throwable t) {
        if (reportedSites.add(site))
            log.error("[{}] failed; further occurrences at this site are suppressed", site, t);
    }

    public static final ResourceFactory RESOURCE_FACTORY = load(ResourceFactory.class);

    private static <T> T load(Class<T> clazz) {
        final T service = ServiceLoader.load(clazz).findFirst().orElseThrow();
        return service;
    }
}
