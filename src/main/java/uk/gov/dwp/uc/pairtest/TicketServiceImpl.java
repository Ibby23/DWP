package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;


public class TicketServiceImpl implements TicketService {

    /** Upper bound on the total number of tickets (all types combined) per purchase. */
    private static final int MAX_TICKETS = 25;

    /** Price per adult ticket in whole pounds. */
    private static final int ADULT_TICKET_PRICE = 25;

    /** Price per child ticket in whole pounds. */
    private static final int CHILD_TICKET_PRICE = 15;

    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    /**
     * Creates the service with injected dependencies.
     *
     * @param ticketPaymentService external payment service (non-null)
     * @param seatReservationService external seat reservation service (non-null)
     */
    public TicketServiceImpl(
            TicketPaymentService ticketPaymentService,
            SeatReservationService seatReservationService
    ) {
        // Deliberately no null-checks here to keep constructor simple; tests inject valid mocks.
        // In production, you might validate and fail fast.
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    /**
     * Validates the request, calculates amount and seats, then performs payment and seat reservation.
     *
     * @param accountId account identifier; must be non-null and &gt; 0
     * @param ticketTypeRequests one or more ticket requests; none may be null
     * @throws InvalidPurchaseException on any business rule violation or malformed request
     */
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {

        // Validate once up front, including counts across all line items.
        validatePurchaseRequest(accountId, ticketTypeRequests);

        // Derived values are computed in separate methods for readability and testability.
        int totalAmount = calculateTotalAmount(ticketTypeRequests);
        int totalSeats = calculateTotalSeats(ticketTypeRequests);

        // According to assumptions, these calls always succeed.
        // If you needed stronger guarantees, you could wrap in a transaction pattern or compensating actions.
        ticketPaymentService.makePayment(accountId, totalAmount);
        seatReservationService.reserveSeat(accountId, totalSeats);
    }

    /**
     * Validates account id, request presence, aggregate quantities and rule constraints.
     *
     * <p>Notes:</p>
     * <ul>
     *   <li>Rejects any request with {@code noOfTickets <= 0}. If you prefer allowing zero
     *       quantities, you can ignore them instead as long as the final non-zero total &gt; 0.</li>
     *   <li>Enforces infants ≤ adults to respect the "lap" constraint.</li>
     * </ul>
     *
     * @param accountId account identifier; must be non-null and &gt; 0
     * @param ticketTypeRequests requests to validate; must not be null/empty or contain null items
     * @throws InvalidPurchaseException if any check fails
     */
    private void validatePurchaseRequest(Long accountId, TicketTypeRequest... ticketTypeRequests) {
        // Basic account validation
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Invalid account ID");
        }

        // Requests array must be present and contain at least one item
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("No tickets requested");
        }

        int totalTickets = 0;
        int adultTickets = 0;
        int childTickets = 0;
        int infantTickets = 0;

        // Aggregate quantities by type while validating each element
        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null) {
                throw new InvalidPurchaseException("Invalid ticket request");
            }

            int noOfTickets = request.getNoOfTickets();

            // Your policy: zero/negative quantities are invalid. (Some teams allow zero and ignore.)
            if (noOfTickets <= 0) {
                throw new InvalidPurchaseException("Invalid number of tickets");
            }

            totalTickets += noOfTickets;

            // Count by type (ADULT/CHILD/INFANT). Enum switch keeps this exhaustive.
            switch (request.getTicketType()) {
                case ADULT:
                    adultTickets += noOfTickets;
                    break;
                case CHILD:
                    childTickets += noOfTickets;
                    break;
                case INFANT:
                    infantTickets += noOfTickets;
                    break;
            }
        }

        // Global cap across all types, including infants
        if (totalTickets > MAX_TICKETS) {
            throw new InvalidPurchaseException("Maximum " + MAX_TICKETS + " tickets per purchase");
        }

        // Require at least one adult if there are any tickets
        if (adultTickets == 0) {
            throw new InvalidPurchaseException("At least one adult ticket is required");
        }

        // Infants must not exceed adults (one lap per adult)
        if (infantTickets > adultTickets) {
            throw new InvalidPurchaseException("Number of infant tickets cannot exceed number of adult tickets");
        }
    }

    /**
     * Computes the total amount to charge in whole pounds.
     * Infants are free by policy.
     *
     * @param ticketTypeRequests validated requests
     * @return total payable in pounds
     */
    private int calculateTotalAmount(TicketTypeRequest... ticketTypeRequests) {
        int totalAmount = 0;

        // Iterate each line item; pricing is deterministic per type.
        for (TicketTypeRequest request : ticketTypeRequests) {
            switch (request.getTicketType()) {
                case ADULT:
                    totalAmount += request.getNoOfTickets() * ADULT_TICKET_PRICE;
                    break;
                case CHILD:
                    totalAmount += request.getNoOfTickets() * CHILD_TICKET_PRICE;
                    break;
                case INFANT:
                    // Infants are free by policy; no addition needed.
                    break;
            }
        }

        return totalAmount;
    }

    /**
     * Computes the count of seats to reserve.
     * Adults and children get seats; infants do not.
     *
     * @param ticketTypeRequests validated requests
     * @return total seats to reserve
     */
    private int calculateTotalSeats(TicketTypeRequest... ticketTypeRequests) {
        int totalSeats = 0;

        // Adults and children are allocated seats; infants sit on laps.
        for (TicketTypeRequest request : ticketTypeRequests) {
            switch (request.getTicketType()) {
                case ADULT:
                case CHILD:
                    totalSeats += request.getNoOfTickets();
                    break;
                case INFANT:
                    // No seat allocation for infants.
                    break;
            }
        }

        return totalSeats;
    }
}
