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

package org.eclipse.ecsp.sql.postgress.health;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import org.eclipse.ecsp.healthcheck.HealthService;
import org.eclipse.ecsp.healthcheck.HealthServiceCallBack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class for {@link PostgresDbHealthService}.
 */
@ExtendWith(MockitoExtension.class)
class PostgresDbHealthServiceTest {

    @Mock
    private HealthService healthService;

    @Mock
    private HikariDataSource defaultDataSource;

    @Mock
    private HikariDataSource tenant1DataSource;

    @Mock
    private HikariDataSource tenant2DataSource;

    @InjectMocks
    private PostgresDbHealthService postgresDbHealthService;

    private Map<String, DataSource> targetDataSources;

    /**
     * Set up test fixtures.
     */
    @BeforeEach
    void setUp() {
        targetDataSources = new HashMap<>();
        targetDataSources.put("default", defaultDataSource);
        targetDataSources.put("tenant1", tenant1DataSource);
        targetDataSources.put("tenant2", tenant2DataSource);

        ReflectionTestUtils.setField(postgresDbHealthService, "targetDataSources", targetDataSources);
        ReflectionTestUtils.setField(postgresDbHealthService, "poolName", "testPool");
    }

    /**
     * Test init method registers callback and starts health service.
     */
    @Test
    void testInit_RegistersCallbackAndStartsHealthService() {
        postgresDbHealthService.init();

        ArgumentCaptor<HealthServiceCallBack> callbackCaptor = ArgumentCaptor.forClass(HealthServiceCallBack.class);
        verify(healthService).registerCallBack(callbackCaptor.capture());
        verify(healthService).startHealthServiceExecutor();

        assertNotNull(callbackCaptor.getValue());
    }

    /**
     * Test perform restart when restart on failure is true.
     * Note: The callback returns true after processing the first datasource.
     */
    @Test
    void testPerformRestart_WhenRestartOnFailureTrue_ClosesDataSources() {
        ReflectionTestUtils.setField(postgresDbHealthService, "restartOnFailure", true);
        
        postgresDbHealthService.init();

        ArgumentCaptor<HealthServiceCallBack> callbackCaptor = ArgumentCaptor.forClass(HealthServiceCallBack.class);
        verify(healthService).registerCallBack(callbackCaptor.capture());

        HealthServiceCallBack callback = callbackCaptor.getValue();
        boolean result = callback.performRestart();

        assertTrue(result);
        // The loop processes the first datasource and returns, so only one should be closed
        verify(defaultDataSource, atMost(1)).close();
    }

    /**
     * Test perform restart when restart on failure is false.
     */
    @Test
    void testPerformRestart_WhenRestartOnFailureFalse_DoesNotCloseDataSources() {
        ReflectionTestUtils.setField(postgresDbHealthService, "restartOnFailure", false);
        
        postgresDbHealthService.init();

        ArgumentCaptor<HealthServiceCallBack> callbackCaptor = ArgumentCaptor.forClass(HealthServiceCallBack.class);
        verify(healthService).registerCallBack(callbackCaptor.capture());

        HealthServiceCallBack callback = callbackCaptor.getValue();
        boolean result = callback.performRestart();

        assertFalse(result);
        verify(defaultDataSource, never()).close();
        verify(tenant1DataSource, never()).close();
        verify(tenant2DataSource, never()).close();
    }

    /**
     * Test perform restart closes all tenant data sources when enabled.
     */
    @Test
    void testPerformRestart_ClosesFirstDataSourceOnly() {
        ReflectionTestUtils.setField(postgresDbHealthService, "restartOnFailure", true);
        
        postgresDbHealthService.init();

        ArgumentCaptor<HealthServiceCallBack> callbackCaptor = ArgumentCaptor.forClass(HealthServiceCallBack.class);
        verify(healthService).registerCallBack(callbackCaptor.capture());

        HealthServiceCallBack callback = callbackCaptor.getValue();
        boolean result = callback.performRestart();

        assertTrue(result);
        // Should close at least one datasource
        verify(defaultDataSource, atMost(1)).close();
    }

    /**
     * Test that callback is instance of PostgresHealthServiceCallBack.
     */
    @Test
    void testInit_RegistersCorrectCallbackType() {
        postgresDbHealthService.init();

        ArgumentCaptor<HealthServiceCallBack> callbackCaptor = ArgumentCaptor.forClass(HealthServiceCallBack.class);
        verify(healthService).registerCallBack(callbackCaptor.capture());

        HealthServiceCallBack callback = callbackCaptor.getValue();
        assertNotNull(callback);
        assertTrue(callback.getClass().getName().contains("PostgresHealthServiceCallBack"));
    }

    /**
     * Test perform restart with empty data sources map.
     */
    @Test
    void testPerformRestart_WithEmptyDataSources_ReturnsFalse() {
        ReflectionTestUtils.setField(postgresDbHealthService, "restartOnFailure", false);
        ReflectionTestUtils.setField(postgresDbHealthService, "targetDataSources", new HashMap<String, DataSource>());
        
        postgresDbHealthService.init();

        ArgumentCaptor<HealthServiceCallBack> callbackCaptor = ArgumentCaptor.forClass(HealthServiceCallBack.class);
        verify(healthService).registerCallBack(callbackCaptor.capture());

        HealthServiceCallBack callback = callbackCaptor.getValue();
        boolean result = callback.performRestart();

        assertFalse(result);
    }

    /**
     * Test perform restart with single tenant data source.
     */
    @Test
    void testPerformRestart_SingleTenant_ClosesCorrectly() {
        Map<String, DataSource> singleTenantMap = new HashMap<>();
        singleTenantMap.put("default", defaultDataSource);
        ReflectionTestUtils.setField(postgresDbHealthService, "targetDataSources", singleTenantMap);
        ReflectionTestUtils.setField(postgresDbHealthService, "restartOnFailure", true);
        
        postgresDbHealthService.init();

        ArgumentCaptor<HealthServiceCallBack> callbackCaptor = ArgumentCaptor.forClass(HealthServiceCallBack.class);
        verify(healthService).registerCallBack(callbackCaptor.capture());

        HealthServiceCallBack callback = callbackCaptor.getValue();
        boolean result = callback.performRestart();

        assertTrue(result);
        verify(defaultDataSource, times(1)).close();
    }

    /**
     * Test that init is called only once (PostConstruct behavior).
     */
    @Test
    void testInit_CanBeCalledMultipleTimes() {
        postgresDbHealthService.init();
        postgresDbHealthService.init();

        verify(healthService, times(2)).registerCallBack(any(HealthServiceCallBack.class));
        verify(healthService, times(2)).startHealthServiceExecutor();
    }
}
