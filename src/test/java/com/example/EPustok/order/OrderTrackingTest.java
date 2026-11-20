package com.example.EPustok.order;
import com.example.EPustok.identity.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderTrackingTest {
@Test void statusUpdateRequiresPreviousStage(){var jdbc=mock(JdbcTemplate.class);var service=new OrderTracking(jdbc,mock(UserRepository.class));UUID id=UUID.randomUUID();when(jdbc.queryForList("SELECT status FROM seller_shipment WHERE order_id=?",String.class,id)).thenReturn(java.util.List.of("PLACED","PLACED"));service.advance(id,OrderTracking.Status.PROCESSING);verify(jdbc).update("UPDATE seller_shipment SET status=? WHERE order_id=?","PROCESSING",id);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.SHIPPED));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.PLACED));}
@Test void invalidPageIsRejected(){var service=new OrderTracking(mock(JdbcTemplate.class),mock(UserRepository.class));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.all(-1));}
}
