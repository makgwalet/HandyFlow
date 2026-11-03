import type { Delivery } from '../types/delivery';

export function routeForDelivery(delivery: Delivery): { screen: string; params: { deliveryId: string } } {
  const params = { deliveryId: delivery.id };
  switch (delivery.status) {
    case 'IN_TRANSIT':
      return { screen: 'InTransit', params };
    case 'ARRIVED':
      return { screen: 'CompleteChecklist', params };
    case 'COMPLETED':
      return { screen: 'DeliveryProof', params };
    case 'PROBLEM':
    case 'SCHEDULED':
    default:
      return { screen: 'DeliveryDetail', params };
  }
}
