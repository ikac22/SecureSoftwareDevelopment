package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.Comment;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.CommentRepository;
import com.zuehlke.securesoftwaredevelopment.service.CarImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class CommentController {
    private static final Logger LOG = LoggerFactory.getLogger(CommentController.class);

    private CommentRepository commentRepository;
    private CarImageStorageService carImageStorageService;

    public CommentController(CommentRepository commentRepository, CarImageStorageService carImageStorageService) {
        this.commentRepository = commentRepository;
        this.carImageStorageService = carImageStorageService;
    }

    @PostMapping(value = "/comments", consumes = "multipart/form-data")
    public ResponseEntity<String> createComment(@RequestParam("carId") int carId,
                                                @RequestParam("comment") String text,
                                                @RequestParam(value = "image", required = false) MultipartFile image,
                                                Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        try {
            String imagePath = carImageStorageService.store(image);
            Comment comment = new Comment(carId, user.getId(), text, imagePath);
            commentRepository.create(comment);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }
}
