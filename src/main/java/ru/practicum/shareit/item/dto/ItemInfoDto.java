package ru.practicum.shareit.item.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.shareit.booking.dto.BookingDateInfoDto;
import ru.practicum.shareit.comment.dto.CommentDto;

import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemInfoDto {
    private Long id;
    private String name;
    private String description;
    private boolean available;
    private Long request;
    private Collection<CommentDto> comments;
    private BookingDateInfoDto lastBooking;
    private BookingDateInfoDto nextBooking;
}
