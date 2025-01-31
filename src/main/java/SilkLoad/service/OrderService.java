package SilkLoad.service;

import SilkLoad.dto.NotificationsRequestDto;
import SilkLoad.dto.OrderBuyAuctionDto;
import SilkLoad.dto.OrderBuyNowDto;
import SilkLoad.dto.OrderHistoryDto;
import SilkLoad.entity.*;
import SilkLoad.entity.ChatRoomEnum.ChatRoomType;
import SilkLoad.entity.NotificationsEnum.NotificationsType;
import SilkLoad.entity.OrderEnum.OrderType;
import SilkLoad.entity.ProductEnum.ProductType;
import SilkLoad.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher applicationEventPublisher;


    @Transactional
    public Orders saveBuyNow(OrderBuyNowDto orderBuyNowDto) {


        Long memberId = orderBuyNowDto.getMemberId();
        Long productId = orderBuyNowDto.getProductId();

        Members member = memberRepository.findById(memberId).get();
        Product product = productRepository.findById(productId).get();


        if( (member != null && product != null) && (product.getMembers().getId() != member.getId()) ) {

            Orders order = createBuyNowOrder(member, product);
            List<Orders> SameTimeOrderList = getSameTimeOrders(order);
            log.info("겹치는 시간 여부 ={}, {}", order.getProduct().getId(), order.getOrderDateTime());

            List<Product> byProduct_idAndProductType = productRepository.findByIdAndProductType(order.getProduct().getId(), ProductType.sale);

            if (SameTimeOrderList.isEmpty() && !byProduct_idAndProductType.isEmpty() ) {

                product.setProductType(ProductType.trading);
                Product saveProduct = productRepository.save(product);
                Orders saveOrder = orderRepository.save(order);

                applicationEventPublisher.publishEvent( NotificationsRequestDto.create( product.getMembers(), member.getName() ,product.getName() , NotificationsType.buyNow));

                if (saveOrder != null && saveProduct != null) {
                    ChatRoom chatRoom = createChatRoom(member, saveProduct);
                    if (chatRoom != null)
                        return saveOrder;
                }
            }

        }
        return null;

    }




    @Transactional
    public Orders saveBuyAuction(OrderBuyAuctionDto orderBuyAuctionDto) {

        Long memberId = orderBuyAuctionDto.getMemberId();
        Long productId = orderBuyAuctionDto.getProductId();


        Optional<Members> optionalMembers = memberRepository.findById(memberId);
        Optional<Product> optionalProduct = productRepository.findById(productId);
        Members member;
        Product product;

        if ( optionalMembers.isPresent() && optionalProduct.isPresent() ) {
            member = optionalMembers.get();
            product = optionalProduct.get();

            if ( (product.getMembers().getId() != member.getId())) {

                Orders order = createBuyAuctionOrder(member, product, orderBuyAuctionDto.getAuctionPrice());
                List<Orders> SameTimeOrderList = getSameTimeOrders(order);
                log.info("겹치는 시간 여부 ={}, {}", order.getProduct().getId(), order.getOrderDateTime());

                //물품 타입을 검사하여 판매중인 상태인지 검사
                List<Product> byProduct_idAndProductType = productRepository.findByIdAndProductType(order.getProduct().getId(), ProductType.sale);
                Long maxAuctionPrice = orderRepository.findByProductIdMaxAuctionPrice(order.getProduct().getId());

                if (SameTimeOrderList.isEmpty() && !byProduct_idAndProductType.isEmpty()
                        && order.getOfferPrice() > maxAuctionPrice
                        && order.getOfferPrice() > order.getProduct().getAuctionPrice()) {

                    Optional<Orders> optionalFindOrder = orderRepository.findByMemberBuyer_IdAndProduct_Id(order.getMemberBuyer().getId(), order.getProduct().getId());
                    Orders saveOrder;

                    if (optionalFindOrder.isPresent()) {

                        saveOrder = optionalFindOrder.get();
                        saveOrder.setOfferPrice(orderBuyAuctionDto.getAuctionPrice());

                    } else {
                        saveOrder = order;
                    }
                    Orders savedOrder = orderRepository.save(saveOrder);
                    applicationEventPublisher.publishEvent(NotificationsRequestDto.create(
                            product.getMembers(), member.getName()
                            , product.getName(),
                            NotificationsType.buyAuction));

                    return savedOrder;
                }

            }

        }
        return null;

    /*    Long memberId = orderBuyAuctionDto.getMemberId();
        Long productId = orderBuyAuctionDto.getProductId();

        Optional<Members> optionalMembers = memberRepository.findById(memberId);

        if ( optionalMembers.isPresent() ) {
            Members member = optionalMembers.get();
        }

        Members member = memberRepository.findById(memberId).get();
        Product product = productRepository.findById(productId).get();


        if( (member != null && product != null) && (product.getMembers().getId() != member.getId()) ) {

            Orders order = createBuyAuctionOrder(member, product, orderBuyAuctionDto.getAuctionPrice());
            List<Orders> SameTimeOrderList = getSameTimeOrders(order);
            log.info("겹치는 시간 여부 ={}, {}", order.getProduct().getId(), order.getOrderDateTime());

            List<Product> byProduct_idAndProductType = productRepository.findByIdAndProductType(order.getProduct().getId(), ProductType.sale);
            Long maxAuctionPrice = orderRepository.findByProductIdMaxAuctionPrice(order.getProduct().getId());

            if (SameTimeOrderList.isEmpty() && !byProduct_idAndProductType.isEmpty()
                    && order.getOfferPrice() > maxAuctionPrice
                    && order.getOfferPrice() > order.getProduct().getAuctionPrice() ) {

                Orders savedOrder = orderRepository.save(order);
                applicationEventPublisher.publishEvent( NotificationsRequestDto.create(product.getMembers(), member.getName() ,product.getName(),NotificationsType.buyAuction));

                return savedOrder;
            }
        }
        return null;*/

    }

    private List<Orders> getSameTimeOrders(Orders order) {
        LocalDateTime orderDateTime = order.getOrderDateTime();
        LocalDateTime generateDateTime = LocalDateTime.of(orderDateTime.getYear(), orderDateTime.getMonthValue(), orderDateTime.getDayOfMonth(),
                orderDateTime.getHour(), orderDateTime.getMinute(), orderDateTime.getSecond());


        List<Orders> SameTimeOrderList = orderRepository.findByProduct_IdAndOrderDateTime(
                order.getProduct().getId(),
                generateDateTime
        );
        return SameTimeOrderList;
    }

    @Transactional
    public Page<OrderHistoryDto> findMemberSaleOrder(Long memberId, Pageable pageable) {

        return orderRepository.findMemberSaleOrder(memberId, pageable);

    }

    @Transactional
    public Page<OrderHistoryDto> findMemberPurchaseOrder (Long memberId, Pageable pageable) {

        return orderRepository.findMemberPurchaseOrder(memberId, pageable);

    }


    @Transactional
    public Orders completeOrder(Long id) {

        Optional<Orders> optionalOrders = orderRepository.findById(id);
        if(optionalOrders.isPresent()) {

            Orders order = optionalOrders.get();

            if( order.getOrderType() == OrderType.successfulBid ||
                    order.getOrderType() == OrderType.buyNow
                    && order.getProduct().getProductType() == ProductType.trading) {
                order.setOrderType(OrderType.complete);
                order.getProduct().setProductType(ProductType.soldOut);
                Orders savedOrder = orderRepository.save(order);

                applicationEventPublisher.publishEvent(NotificationsRequestDto.create(
                        order.getMemberBuyer(),
                        order.getProduct().getMembers().getName() ,
                        order.getProduct().getName(),
                        NotificationsType.completion) );

                return savedOrder;
            }
        }
        return null;
    }

    @Transactional
    public Orders tradingOrder(Long id) {

        Optional<Orders> optionalOrders = orderRepository.findById(id);
        if(optionalOrders.isPresent()) {

            Orders order = optionalOrders.get();

            if(  order.getOrderType() == OrderType.bid
                    &&  order.getProduct().getProductType() == ProductType.sale) {

                order.setOrderType(OrderType.successfulBid);
                order.getProduct().setProductType(ProductType.trading );
                ChatRoom chatRoom = createChatRoom(order.getMemberBuyer(), order.getProduct());
                if (chatRoom != null) {
                    Orders savedOrder = orderRepository.save(order);
                    applicationEventPublisher.publishEvent(NotificationsRequestDto.create(
                            order.getMemberBuyer(),
                            order.getProduct().getMembers().getName(),
                            order.getProduct().getName(),
                            NotificationsType.trade));

                    return savedOrder;
                }

            }
        }
        return null;
    }

    @Transactional
    public Orders cancelOrder(Long id) {

        Optional<Orders> optionalOrders = orderRepository.findById(id);
        if(optionalOrders.isPresent()) {

            Orders order = optionalOrders.get();

            if( (order.getOrderType() != OrderType.complete && order.getOrderType() != OrderType.cancel)
                    &&  (order.getProduct().getProductType() != ProductType.cancel || order.getProduct().getProductType() != ProductType.soldOut) ) {

                order.setOrderType(OrderType.cancel );
                order.getProduct().setProductType(ProductType.cancel );
                Orders savedOrder = orderRepository.save(order);
                applicationEventPublisher.publishEvent( NotificationsRequestDto.create(
                        savedOrder.getMemberBuyer(),
                        savedOrder.getProduct().getMembers().getName(),
                        savedOrder.getProduct().getName(),
                        NotificationsType.cancle ));
                return savedOrder;

            }
        }
        return null;
    }


    @Transactional
    public Long findByMaxAuctionPrice( Long productId) {

        return orderRepository.findByProductIdMaxAuctionPrice(productId);

    }


    //즉시 거래일 때의 createOrder
   private Orders createBuyAuctionOrder(Members member, Product product, Long offerPrice) {

       Orders order = Orders.builder()
               .memberBuyer(member)
               .product(product)
               .offerPrice(offerPrice)
               .orderType(OrderType.bid )
               .orderDateTime(LocalDateTime.now())
               .build();
       return  order;

   }

    private Orders createBuyNowOrder(Members member, Product product) {

        Orders order = Orders.builder()
                .memberBuyer(member)
                .product(product)
                .offerPrice(product.getInstantPrice())
                .orderType(OrderType.buyNow )
                .orderDateTime(LocalDateTime.now())
                .build();

        return  order;

    }

    private ChatRoom createChatRoom(Members members, Product product) {

        ChatRoom chatRoom = ChatRoom.builder()
                .name(product.getMembers().getName() + "님의 " +  product.getName() + " 방")
                .membersBuyer(members)
                .product(product)
                .chatRoomType(ChatRoomType.open)
                .createDateTime(LocalDateTime.now())
                .build();

        return  chatRoomRepository.save(chatRoom);

    }

}
