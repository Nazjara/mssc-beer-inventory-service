package com.nazjara.service;

import com.nazjara.domain.BeerInventory;
import com.nazjara.model.BeerOrderDto;
import com.nazjara.model.BeerOrderLineDto;
import com.nazjara.repositories.BeerInventoryRepository;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class AllocationServiceImpl implements AllocationService {

    private final BeerInventoryRepository beerInventoryRepository;

    @Override
    public boolean allocateOrder(BeerOrderDto beerOrderDto) {
        log.debug("Allocating OrderId: " + beerOrderDto.getId());

        var totalOrdered = new AtomicInteger();
        var totalAllocated = new AtomicInteger();

        beerOrderDto.getBeerOrderLines().forEach(beerOrderLine -> {
            if ((((beerOrderLine.getOrderQuantity() != null ? beerOrderLine.getOrderQuantity() : 0)
                    - (beerOrderLine.getQuantityAllocated() != null ? beerOrderLine.getQuantityAllocated() : 0)) > 0)) {
                allocateBeerOrderLine(beerOrderLine);
            }
            totalOrdered.set(totalOrdered.get() + beerOrderLine.getOrderQuantity());
            totalAllocated.set(totalAllocated.get() + (beerOrderLine.getQuantityAllocated() != null ? beerOrderLine.getQuantityAllocated() : 0));
        });

        log.debug("Total Ordered: " + totalOrdered.get() + " Total Allocated: " + totalAllocated.get());

        return totalOrdered.get() == totalAllocated.get();
    }

    private void allocateBeerOrderLine(BeerOrderLineDto beerOrderLine) {
        var beerInventoryList = beerInventoryRepository.findAllByUpc(beerOrderLine.getUpc());

        beerInventoryList.forEach(beerInventory -> {
            int inventory = (beerInventory.getQuantityOnHand() == null) ? 0 : beerInventory.getQuantityOnHand();
            int orderQty = (beerOrderLine.getOrderQuantity() == null) ? 0 : beerOrderLine.getOrderQuantity();
            int allocatedQty = (beerOrderLine.getQuantityAllocated() == null) ? 0 : beerOrderLine.getQuantityAllocated();
            int qtyToAllocate = orderQty - allocatedQty;

            if (inventory >= qtyToAllocate) { // full allocation
                inventory = inventory - qtyToAllocate;
                beerOrderLine.setQuantityAllocated(orderQty);
                beerInventory.setQuantityOnHand(inventory);

                beerInventoryRepository.save(beerInventory);
            } else if (inventory > 0) { //partial allocation
                beerOrderLine.setQuantityAllocated(allocatedQty + inventory);
                beerInventory.setQuantityOnHand(0);

            }

            if (beerInventory.getQuantityOnHand() == 0) {
                beerInventoryRepository.delete(beerInventory);
            }
        });

    }

    @Override
    public void deallocateOrder(BeerOrderDto beerOrderDto) {
        beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
            BeerInventory beerInventory = BeerInventory.builder()
                    .beerId(beerOrderLineDto.getBeerId())
                    .upc(beerOrderLineDto.getUpc())
                    .quantityOnHand(beerOrderLineDto.getQuantityAllocated())
                    .build();

            var savedInventory = beerInventoryRepository.save(beerInventory);

            log.debug("Saved Inventory for beer upc: " + savedInventory.getUpc() + " inventory id: " + savedInventory.getId());
        });
    }
}
