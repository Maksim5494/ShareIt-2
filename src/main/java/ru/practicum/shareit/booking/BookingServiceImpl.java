package ru.practicum.shareit.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.BookingRequestDto;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.UserRepository;
import ru.practicum.shareit.user.model.User;

import java.time.LocalDateTime;
import java.util.Collection;

@RequiredArgsConstructor
@Service
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;

    @Override
    public BookingDto create(Long userId, BookingRequestDto bookingRequestDto) {
        validate(userId, bookingRequestDto);
        Item item = itemRepository.findById(bookingRequestDto.getItemId())
                .orElseThrow(() -> new NotFoundException("ItemId = " + bookingRequestDto.getItemId() + " not found!"));
        User booker = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User id = " + userId + " not found!"));
        return BookingMapper.toBookingDto(bookingRepository.save(
                Booking.builder()
                        .start(bookingRequestDto.getStart())
                        .end(bookingRequestDto.getEnd())
                        .item(item)
                        .booker(booker)
                        .status(BookingStatus.WAITING)
                        .build()
        ));
    }

    @Override
    public BookingDto setApproved(Long userId, Long bookingId, Boolean approved) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking id = " + bookingId + " not found!"));

        if (!booking.getItem().getOwner().getId().equals(userId)) {
            throw new NotFoundException("Only owner can set approved!");
        }

        if (BookingStatus.APPROVED.equals(booking.getStatus())) {
            throw new ValidationException("Status is already APPROVED!");
        }

        booking.setStatus(approved ? BookingStatus.APPROVED : BookingStatus.REJECTED);
        return BookingMapper.toBookingDto(bookingRepository.save(booking));
    }

    @Override
    public BookingDto findById(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking id = " + bookingId + " not found!"));
        if (!booking.getBooker().getId().equals(userId) && !booking.getItem().getOwner().getId().equals(userId)) {
            throw new NotFoundException("Only owner or booker can get Booking!");
        }
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User id = " + userId + " not found!");
        }
        return BookingMapper.toBookingDto(booking);
    }

    public Collection<BookingDto> findAllByBookerAndStatus(Long userId, String stateStr) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User id = " + userId + " not found!");
        }

        BookingState state = BookingState.from(stateStr);
        LocalDateTime now = LocalDateTime.now();

        Collection<Booking> bookings = switch (state) {
            case ALL -> bookingRepository.findAllByBookerIdOrderByStartDesc(userId);
            case CURRENT -> bookingRepository.findAllByBookerIdAndStartBeforeAndEndAfterOrderByStartDesc(userId, now, now);
            case PAST -> bookingRepository.findAllByBookerIdAndEndBeforeOrderByStartDesc(userId, now);
            case FUTURE -> bookingRepository.findAllByBookerIdAndStartAfterOrderByStartDesc(userId, now);
            case WAITING -> bookingRepository.findAllByBookerIdAndStatusOrderByStartDesc(userId, BookingStatus.WAITING);
            case REJECTED -> bookingRepository.findAllByBookerIdAndStatusOrderByStartDesc(userId, BookingStatus.REJECTED);
        };

        return bookings.stream()
                .map(BookingMapper::toBookingDto)
                .toList();
    }

    @Override
    public Collection<BookingDto> findAllByOwnerAndStatus(Long userId, String stateStr) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User id = " + userId + " not found!");
        }

        BookingState state = BookingState.from(stateStr);
        LocalDateTime now = LocalDateTime.now();

        Collection<Booking> bookings = switch (state) {
            case ALL -> bookingRepository.findAllByItemOwnerIdOrderByStartDesc(userId);
            case CURRENT -> bookingRepository.findAllByItemOwnerIdAndStartBeforeAndEndAfterOrderByStartDesc(userId, now, now);
            case PAST -> bookingRepository.findAllByItemOwnerIdAndEndBeforeOrderByStartDesc(userId, now);
            case FUTURE -> bookingRepository.findAllByItemOwnerIdAndStartAfterOrderByStartDesc(userId, now);
            case WAITING -> bookingRepository.findAllByItemOwnerIdAndStatusOrderByStartDesc(userId, BookingStatus.WAITING);
            case REJECTED -> bookingRepository.findAllByItemOwnerIdAndStatusOrderByStartDesc(userId, BookingStatus.REJECTED);
        };

        return bookings.stream()
                .map(BookingMapper::toBookingDto)
                .toList();
    }

    private void validate(Long userId, BookingRequestDto bookingRequestDto) {
        LocalDateTime now = LocalDateTime.now();

        if (bookingRequestDto.getStart() == null || bookingRequestDto.getEnd() == null) {
            throw new ValidationException("Dates cannot be null");
        }

        if (bookingRequestDto.getStart().isBefore(now)) {
            throw new ValidationException("Start date cannot be in the past");
        }
        if (bookingRequestDto.getEnd().isBefore(now) || bookingRequestDto.getEnd().isBefore(bookingRequestDto.getStart())) {
            throw new ValidationException("Invalid end date");
        }

        if (bookingRequestDto.getEnd().equals(bookingRequestDto.getStart())) {
            throw new ValidationException("End date equals Start date!");
        }

        Item item = itemRepository.findById(bookingRequestDto.getItemId())
                .orElseThrow(() -> new NotFoundException("Item id = " + bookingRequestDto.getItemId() + " not found!"));

        if (item.getOwner().getId().equals(userId)) {
            throw new NotFoundException("Owner cannot book their own item");
        }

        if (Boolean.FALSE.equals(item.getAvailable())) {
            throw new ValidationException("Item is not available");
        }
    }
}

