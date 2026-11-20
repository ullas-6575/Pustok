package com.example.EPustok.order;
import com.example.EPustok.identity.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderTrackingTest {
@Test void statusUpdateRequiresPreviousStage(){var jdbc=mock(JdbcTemplate.class);var service=new OrderTracking(jdbc,mock(UserRepository.class));UUID id=UUID.randomUUID();when(jdbc.update("UPDATE purchase_order SET status=? WHERE id=? AND status=?","PROCESSING",id,"PLACED")).thenReturn(1);service.advance(id,OrderTracking.Status.PROCESSING);verify(jdbc).update("UPDATE purchase_order SET status=? WHERE id=? AND status=?","PROCESSING",id,"PLACED");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.SHIPPED));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.PLACED));}
@Test void invalidPageIsRejected(){var service=new OrderTracking(mock(JdbcTemplate.class),mock(UserRepository.class));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.all(-1));}
}
