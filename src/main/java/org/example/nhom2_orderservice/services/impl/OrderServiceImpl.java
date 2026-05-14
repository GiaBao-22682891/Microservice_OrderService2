package org.example.nhom2_orderservice.services.impl;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.example.nhom2_orderservice.dto.OrderItemDTO;
import org.example.nhom2_orderservice.dto.requests.OrderCreateRequest;
import org.example.nhom2_orderservice.dto.requests.OrderItemCreateRequest;
import org.example.nhom2_orderservice.models.Order;
import org.example.nhom2_orderservice.models.OrderItem;
import org.example.nhom2_orderservice.models.enumerate.Status;
import org.example.nhom2_orderservice.repositories.OrderRepository;
import org.example.nhom2_orderservice.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.services.food-service.url}")
    private String foodServiceUrl;

    // Gắn bộ quy tắc "FoodService" đã định nghĩa ở properties
    @Retry(name = "FoodService", fallbackMethod = "createOrderFallback")
    @CircuitBreaker(name = "FoodService", fallbackMethod = "createOrderFallback")
    @RateLimiter(name = "FoodService")
    @Override
    public Order createOrder(String userId, OrderCreateRequest request) {
        System.out.println(">>> BẮT ĐẦU XỬ LÝ TẠO ĐƠN HÀNG CHO USER: " + userId);
        String contextPath = "/api/foods/";
        double totalOrderPrice = 0;
        Order order = new Order();

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemCreateRequest item : request.getItems()) {
            OrderItem orderItem = new OrderItem();
            String foodUrl = foodServiceUrl + contextPath + item.getFoodId();
            OrderItemDTO food = restTemplate.getForObject(foodUrl, OrderItemDTO.class);

            if (food != null) {
                orderItem.setPrice(food.getPrice());
                orderItem.setFoodId(item.getFoodId());
                orderItem.setQuantity(item.getQuantity());
                orderItem.setOrder(order);

                totalOrderPrice += food.getPrice() * item.getQuantity();

                orderItems.add(orderItem);
            }
        }

        order.setUserId(Long.parseLong(userId));
        order.setTotalPrice(totalOrderPrice);
        order.setStatus(Status.PENDING);
        order.setOrderItems(orderItems);

        return orderRepository.save(order);
    }

    // Hàm này sẽ chạy khi FoodService bị sập hoặc quá tải
    public Order createOrderFallback(String userId, OrderCreateRequest request, Throwable t) {
        // Log lỗi ra console để debug
        System.err.println("Không thể tạo đơn hàng do lỗi kết nối Food Service: " + t.getMessage());
        throw new RuntimeException("Dịch vụ kiểm tra món ăn đang quá tải hoặc bảo trì. Vui lòng thử đặt hàng lại sau ít phút!");
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public List<Order> getMyOrders(String userId) {
        return orderRepository.getOrderByUserId(userId);
    }
}
