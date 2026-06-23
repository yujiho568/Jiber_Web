package com.jiber.backend.favorite;

import com.jiber.backend.property.PropertyType;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FavoriteMapper {

    Optional<PropertyType> findPropertyTypeById(@Param("propertyId") Long propertyId);

    List<FavoriteApartmentRow> findFavoriteApartments(@Param("userId") Long userId);

    Optional<FavoriteApartmentRow> findFavoriteApartment(
            @Param("userId") Long userId,
            @Param("propertyId") Long propertyId
    );

    int insertFavoriteApartment(
            @Param("userId") Long userId,
            @Param("propertyId") Long propertyId
    );

    int deleteFavoriteApartment(
            @Param("userId") Long userId,
            @Param("propertyId") Long propertyId
    );

    boolean existsFavoriteApartment(
            @Param("userId") Long userId,
            @Param("propertyId") Long propertyId
    );

    List<FavoriteAreaRow> findFavoriteAreas(@Param("userId") Long userId);

    Optional<FavoriteAreaRow> findFavoriteAreaByNormalizedKey(
            @Param("userId") Long userId,
            @Param("normalizedKey") String normalizedKey
    );

    int insertFavoriteArea(@Param("command") FavoriteAreaInsertCommand command);

    int deleteFavoriteArea(
            @Param("userId") Long userId,
            @Param("favoriteAreaId") Long favoriteAreaId
    );

    boolean existsFavoriteAreaByNormalizedKey(
            @Param("userId") Long userId,
            @Param("normalizedKey") String normalizedKey
    );
}
