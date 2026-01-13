/*
 *
 *
 *   ******************************************************************************
 *
 *    Copyright (c) 2023-24 Harman International
 *
 *
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *
 *    you may not use this file except in compliance with the License.
 *
 *    You may obtain a copy of the License at
 *
 *
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *
 *    Unless required by applicable law or agreed to in writing, software
 *
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *    See the License for the specific language governing permissions and
 *
 *    limitations under the License.
 *
 *
 *
 *    SPDX-License-Identifier: Apache-2.0
 *
 *    *******************************************************************************
 *
 *
 */

package org.eclipse.ecsp.sql.multitenancy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

import org.eclipse.ecsp.sql.dao.constants.MultitenantConstants;
import org.eclipse.ecsp.sql.exception.TargetDataSourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test class for {@link TenantRoutingDataSource}.
 */
@ExtendWith(MockitoExtension.class)
class TenantRoutingDataSourceTest {

    @Mock
    private DataSource defaultDataSource;

    @Mock
    private DataSource tenant1DataSource;

    @Mock
    private DataSource tenant2DataSource;

    private TenantRoutingDataSource routingDataSource;

    /**
     * Set up test fixtures.
     */
    @BeforeEach
    void setUp() {
        routingDataSource = new TenantRoutingDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(MultitenantConstants.DEFAULT_TENANT_ID, defaultDataSource);
        targetDataSources.put("tenant1", tenant1DataSource);
        targetDataSources.put("tenant2", tenant2DataSource);
        
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        routingDataSource.afterPropertiesSet();
        
        // Clear tenant context before each test
        TenantContext.clear();
    }

    /**
     * Tear down test fixtures.
     */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * Test determine current lookup key when multitenancy is disabled.
     */
    @Test
    void testDetermineCurrentLookupKey_MultitenancyDisabled() {
        // Initialize TenantContext with multitenancy disabled
        TenantContext.initialize(false);
        
        Object lookupKey = ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey");
        
        assertEquals(MultitenantConstants.DEFAULT_TENANT_ID, lookupKey);
    }

    /**
     * Test determine current lookup key when multitenancy is enabled with valid tenant.
     */
    @Test
    void testDetermineCurrentLookupKey_MultitenancyEnabled_ValidTenant() {
        // Initialize TenantContext with multitenancy enabled
        TenantContext.initialize(true);
        TenantContext.setCurrentTenant("tenant1");
        
        Object lookupKey = ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey");
        
        assertEquals("tenant1", lookupKey);
    }

    /**
     * Test determine current lookup key when multitenancy is enabled with default tenant.
     */
    @Test
    void testDetermineCurrentLookupKey_MultitenancyEnabled_DefaultTenant() {
        TenantContext.initialize(true);
        TenantContext.setCurrentTenant("tenant2");
        
        Object lookupKey = ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey");
        
        assertEquals("tenant2", lookupKey);
    }

    /**
     * Test determine target data source returns correct data source for valid tenant.
     */
    @Test
    void testDetermineTargetDataSource_ValidTenant() {
        TenantContext.initialize(true);
        TenantContext.setCurrentTenant("tenant1");
        
        DataSource result = routingDataSource.determineTargetDataSource();
        
        assertNotNull(result);
        assertEquals(tenant1DataSource, result);
    }

    /**
     * Test determine target data source returns default when multitenancy disabled.
     */
    @Test
    void testDetermineTargetDataSource_MultitenancyDisabled() {
        TenantContext.initialize(false);
        
        DataSource result = routingDataSource.determineTargetDataSource();
        
        assertNotNull(result);
        assertEquals(defaultDataSource, result);
    }

    /**
     * Test determine target data source throws exception for invalid tenant.
     * Note: Spring's AbstractRoutingDataSource falls back to default if lenient mode is enabled.
     */
    @Test
    void testDetermineTargetDataSource_InvalidTenant_FallsBackToDefault() {
        TenantContext.initialize(true);
        TenantContext.setCurrentTenant("nonexistent-tenant");
        
        // With lenientFallback enabled (default), it should return default datasource
        DataSource result = routingDataSource.determineTargetDataSource();
        
        // Should fall back to default datasource
        assertNotNull(result);
        assertEquals(defaultDataSource, result);
    }

    /**
     * Test determine target data source with null tenant in single-tenant mode.
     */
    @Test
    void testDetermineTargetDataSource_SingleTenant_UsesDefault() {
        TenantContext.initialize(false);
        // Don't set any tenant
        
        DataSource result = routingDataSource.determineTargetDataSource();
        
        assertNotNull(result);
        assertEquals(defaultDataSource, result);
    }

    /**
     * Test routing between multiple tenants.
     */
    @Test
    void testRouting_MultipleTenants() {
        TenantContext.initialize(true);
        
        // Test routing to tenant1
        TenantContext.setCurrentTenant("tenant1");
        DataSource ds1 = routingDataSource.determineTargetDataSource();
        assertEquals(tenant1DataSource, ds1);
        TenantContext.clear();
        
        // Test routing to tenant2
        TenantContext.setCurrentTenant("tenant2");
        DataSource ds2 = routingDataSource.determineTargetDataSource();
        assertEquals(tenant2DataSource, ds2);
        TenantContext.clear();
        
        // Test routing to default
        TenantContext.initialize(false);
        DataSource ds3 = routingDataSource.determineTargetDataSource();
        assertEquals(defaultDataSource, ds3);
    }

    /**
     * Test exception is thrown when lenient fallback is disabled.
     */
    @Test
    void testExceptionWithStrictMode() {
        routingDataSource.setLenientFallback(false);
        TenantContext.initialize(true);
        TenantContext.setCurrentTenant("invalid-tenant-123");
        
        // With lenientFallback disabled, should throw TargetDataSourceNotFoundException
        TargetDataSourceNotFoundException exception = assertThrows(
            TargetDataSourceNotFoundException.class,
            () -> routingDataSource.determineTargetDataSource()
        );
        
        String message = exception.getMessage();
        assertTrue(message.contains("invalid-tenant-123"));
        assertTrue(message.contains("No DataSource configured"));
    }
}
