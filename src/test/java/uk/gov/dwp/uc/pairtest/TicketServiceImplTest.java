package uk.gov.dwp.uc.pairtest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;


class TicketServiceImplTest {

    private TicketServiceImpl ticketService;
    private TicketPaymentService paymentService;
    private SeatReservationService reservationService;

    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class);
        reservationService = mock(SeatReservationService.class);
        ticketService = new TicketServiceImpl(paymentService, reservationService);
    }

    @Nested
    @DisplayName("Account Validation Tests")
    class AccountValidationTests {

        @Test
        @DisplayName("Should throw exception when account ID is null")
        void shouldThrowExceptionWhenAccountIdIsNull() {
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(null, request));

            // Ensure no external side effects when validation fails
            verifyNoInteractions(paymentService, reservationService);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1, -100})
        @DisplayName("Should throw exception when account ID is not positive")
        void shouldThrowExceptionWhenAccountIdIsNotPositive(long accountId) {
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, request));

            verifyNoInteractions(paymentService, reservationService);
        }
    }

    @Nested
    @DisplayName("Ticket Request Validation Tests")
    class TicketRequestValidationTests {

        @Test
        @DisplayName("Should throw exception when ticket requests array is null")
        void shouldThrowExceptionWhenTicketRequestsIsNull() {
            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, (TicketTypeRequest[]) null));

            verifyNoInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should throw exception when ticket requests array is empty")
        void shouldThrowExceptionWhenTicketRequestsIsEmpty() {
            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L));

            verifyNoInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should throw exception when individual ticket request is null")
        void shouldThrowExceptionWhenIndividualTicketRequestIsNull() {
            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, (TicketTypeRequest) null));

            verifyNoInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should throw exception when number of tickets is zero")
        void shouldThrowExceptionWhenNumberOfTicketsIsZero() {
            // Note: if TicketTypeRequest becomes immutable-with-validation, this would throw on construction instead.
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 0);

            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, request));

            verifyNoInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should throw exception when number of tickets is negative")
        void shouldThrowExceptionWhenNumberOfTicketsIsNegative() {
            // Note: if TicketTypeRequest validates input, this will become IllegalArgumentException at construction time.
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, -1);

            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, request));

            verifyNoInteractions(paymentService, reservationService);
        }
    }

    @Nested
    @DisplayName("Business Rule Tests")
    class BusinessRuleTests {

        @Test
        @DisplayName("Should throw exception when total tickets exceed maximum limit")
        void shouldThrowExceptionWhenTotalTicketsExceedMaximum() {
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 26);

            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, request));

            verifyNoInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should throw exception when no adult ticket is requested")
        void shouldThrowExceptionWhenNoAdultTicketRequested() {
            TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);
            TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);

            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, childRequest, infantRequest));

            verifyNoInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should throw exception when infant tickets exceed adult tickets")
        void shouldThrowExceptionWhenInfantTicketsExceedAdultTickets() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);
            TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 2);

            assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, adultRequest, infantRequest));

            verifyNoInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should allow valid purchase with all ticket types")
        void shouldAllowValidPurchaseWithAllTicketTypes() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
            TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 2);
            TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);

            ticketService.purchaseTickets(1L, adultRequest, childRequest, infantRequest);

            // Adults: 2 × £25 = £50; Children: 2 × £15 = £30; Infants: £0
            verify(paymentService).makePayment(1L, 80);

            // Seats: adults + children = 2 + 2 = 4
            verify(reservationService).reserveSeat(1L, 4);

            verifyNoMoreInteractions(paymentService, reservationService);
        }
    }

    @Nested
    @DisplayName("Payment Calculation Tests")
    class PaymentCalculationTests {

        @Test
        @DisplayName("Should calculate correct payment for adult tickets only")
        void shouldCalculateCorrectPaymentForAdultTickets() {
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 3);

            ticketService.purchaseTickets(1L, request);

            verify(paymentService).makePayment(1L, 75); // 3 × £25
            verify(reservationService).reserveSeat(1L, 3);
            verifyNoMoreInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should calculate correct payment for adult and child tickets")
        void shouldCalculateCorrectPaymentForAdultAndChildTickets() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
            TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 3);

            ticketService.purchaseTickets(1L, adultRequest, childRequest);

            verify(paymentService).makePayment(1L, 95); // (2 × £25) + (3 × £15)
            verify(reservationService).reserveSeat(1L, 5);
            verifyNoMoreInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should not charge for infant tickets")
        void shouldNotChargeForInfantTickets() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
            TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 2);

            ticketService.purchaseTickets(1L, adultRequest, infantRequest);

            verify(paymentService).makePayment(1L, 50); // 2 × £25
            verify(reservationService).reserveSeat(1L, 2);
            verifyNoMoreInteractions(paymentService, reservationService);
        }
    }

    @Nested
    @DisplayName("Seat Reservation Tests")
    class SeatReservationTests {

        @Test
        @DisplayName("Should reserve seats for adults only")
        void shouldReserveSeatsForAdultsOnly() {
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 3);

            ticketService.purchaseTickets(1L, request);

            verify(reservationService).reserveSeat(1L, 3);
            verify(paymentService).makePayment(1L, 75);
            verifyNoMoreInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should reserve seats for adults and children")
        void shouldReserveSeatsForAdultsAndChildren() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
            TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 2);

            ticketService.purchaseTickets(1L, adultRequest, childRequest);

            verify(reservationService).reserveSeat(1L, 4);
            verify(paymentService).makePayment(1L, 80);
            verifyNoMoreInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should not reserve seats for infants")
        void shouldNotReserveSeatsForInfants() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
            TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 2);

            ticketService.purchaseTickets(1L, adultRequest, infantRequest);

            verify(reservationService).reserveSeat(1L, 2); // infants have no seats
            verify(paymentService).makePayment(1L, 50);
            verifyNoMoreInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should handle maximum allowed tickets")
        void shouldHandleMaximumAllowedTickets() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 15);
            TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 10);

            ticketService.purchaseTickets(1L, adultRequest, childRequest);

            verify(reservationService).reserveSeat(1L, 25);
            verify(paymentService).makePayment(1L, 525); // (15 × £25) + (10 × £15)
            verifyNoMoreInteractions(paymentService, reservationService);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should process single adult ticket purchase")
        void shouldProcessSingleAdultTicketPurchase() {
            TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

            ticketService.purchaseTickets(1L, request);

            verify(paymentService).makePayment(1L, 25);
            verify(reservationService).reserveSeat(1L, 1);
            verifyNoMoreInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should process purchase with maximum infants per adult")
        void shouldProcessPurchaseWithMaximumInfantsPerAdult() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
            TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 2);

            ticketService.purchaseTickets(1L, adultRequest, infantRequest);

            verify(paymentService).makePayment(1L, 50); // adults only
            verify(reservationService).reserveSeat(1L, 2); // adults only
            verifyNoMoreInteractions(paymentService, reservationService);
        }

        @Test
        @DisplayName("Should handle multiple ticket requests of same type (aggregation)")
        void shouldHandleMultipleTicketRequestsOfSameType() {
            TicketTypeRequest adultRequest1 = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
            TicketTypeRequest adultRequest2 = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

            ticketService.purchaseTickets(1L, adultRequest1, adultRequest2);

            verify(paymentService).makePayment(1L, 75); // 3 × £25
            verify(reservationService).reserveSeat(1L, 3);
            verifyNoMoreInteractions(paymentService, reservationService);
        }
    }
}
