package ru.practicum.shareit.item;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.booking.BookingMapper;
import ru.practicum.shareit.booking.BookingRepository;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.comment.CommentMapper;
import ru.practicum.shareit.comment.CommentRepository;
import ru.practicum.shareit.comment.dto.CommentDto;
import ru.practicum.shareit.comment.dto.CommentRequestDto;
import ru.practicum.shareit.comment.model.Comment;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemInfoDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final CommentRepository commentRepository;

    @Override
    public List<ItemDto> findAll() {
        log.info("Начало процесса получения всех вещей");
        List<Item> items = itemRepository.findAll();
        log.info("Список всех вещей получен");
        return items.stream()
                .map(ItemMapper::toItemDto)
                .toList();
    }

    @Override
    public ItemDto create(Long userId, ItemDto itemDto) {
        return ItemMapper.toItemDto(itemRepository.save(
                Item.builder()
                        .name(itemDto.getName())
                        .owner(userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User id = " + userId + " not found!")))
                        .description(itemDto.getDescription())
                        .available(itemDto.getAvailable())
                        .request(null)
                        .build()
        ));
    }

    @Override
    public ItemDto update(Long id, ItemDto itemDto, Long itemId) {
        validation(id, itemId);

        Item item = itemRepository.findById(itemId).orElseThrow(() -> {
            throw new NotFoundException("Item id = " + itemId + " not found!");
        });
        if (itemDto.getName() != null
                && !itemDto.getName().isBlank()) {
            item.setName(itemDto.getName());
        }
        if (itemDto.getDescription() != null
                && !itemDto.getDescription().isBlank()) {
            item.setDescription(itemDto.getDescription());
        }
        if (itemDto.getAvailable() != null) {
            item.setAvailable(itemDto.getAvailable());
        }
        return ItemMapper.toItemDto(itemRepository.save(item));
    }

    @Override
    public CommentDto addComment(Long userId, Long itemId, CommentRequestDto commentRequestDto) {
        if (commentRequestDto == null || commentRequestDto.getText().isEmpty() || commentRequestDto.getText().isBlank()) {
            throw new ValidationException("Comment is empty!");
        }
        if (bookingRepository.findAllByBookerIdAndItemIdAndStatusAndEndBefore(userId, itemId, BookingStatus.APPROVED, LocalDateTime.now()).isEmpty()) {
            throw new ValidationException("The user (id = " + userId + ") did not book this item (id = " + itemId + ") for rent");
        }
        return CommentMapper.toCommentDto(commentRepository.save(Comment.builder()
                .author(userRepository.findById(userId).orElseThrow(() -> {
                    throw new NotFoundException("User id = " + userId + " not found!");
                }))
                .item(itemRepository.findById(itemId).orElseThrow(() -> {
                    throw new NotFoundException("Item id = " + itemId + " not found!");
                }))
                .text(commentRequestDto.getText())
                .created(LocalDateTime.now())
                .build()));
    }


    @Override
    public ItemInfoDto findItemById(Long userId, Long itemId) {
        log.info("Начало процесса получения вещи по id = {}", itemId);
        Item item = itemRepository.findById(itemId).orElseThrow(() -> {
            throw new NotFoundException("Item (id = " + itemId + ") not found!");
        });
        log.info("Вещь получена");
        return ItemMapper.toItemInfoDto(
                item,
                BookingMapper.toBookingDateInfoDto(bookingRepository.findFirstByItemIdAndItemOwnerIdAndStartBeforeAndStatusOrderByStartDesc(itemId, userId, LocalDateTime.now(), BookingStatus.APPROVED).orElse(null)),
                BookingMapper.toBookingDateInfoDto(bookingRepository.findFirstByItemIdAndItemOwnerIdAndStartAfterAndStatusOrderByStartAsc(itemId, userId, LocalDateTime.now(), BookingStatus.APPROVED).orElse(null)),
                CommentMapper.toCommentsDtoCollection(commentRepository.findAllByItemId(itemId))
        );
    }

    @Override
    public List<ItemInfoDto> findItemsByUserId(Long userId) {
        List<Item> items = itemRepository.findAllByOwnerIdOrderByIdAsc(userId);
        return itemRepository.findAllByOwnerIdOrderByIdAsc(userId).stream()
                .map(item -> ItemMapper.toItemInfoDto(item,
                        BookingMapper.toBookingDateInfoDto(item.getBookings().isEmpty() ? null : item.getBookings().getFirst()),
                        BookingMapper.toBookingDateInfoDto(item.getBookings().isEmpty() ? null : item.getBookings().getLast()),
                        CommentMapper.toCommentsDtoCollection(item.getComments())))
                .toList();
    }

    @Override
    public void delete(Long itemId) {
        itemRepository.deleteById(itemId);
    }

    @Override
    public List<ItemDto> findItemsByText(String text) {
        if (text.isBlank() || text.isEmpty()) return List.of();
        return ItemMapper.toItemsDtoCollection(itemRepository.findByNameOrDescriptionContaining(text));
    }

    private void validation(Long userId, Long itemId) {

        if (itemId != null && !(Objects.equals(Objects.requireNonNull(itemRepository.findById(itemId).orElse(null)).getOwner().getId(), userId))) {
            throw new NotFoundException("Only the owner can edit an item!");
        }
        if (itemId != null && !itemRepository.existsById(itemId)) {
            throw new NotFoundException("Item (id = " + itemId + ") not found!");
        }
    }

}
