package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.domain.*;
import com.zuehlke.securesoftwaredevelopment.repository.CarRepository;
import com.zuehlke.securesoftwaredevelopment.repository.CommentRepository;
import com.zuehlke.securesoftwaredevelopment.repository.PersonRepository;
import com.zuehlke.securesoftwaredevelopment.service.CarImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class CarsController {

    private static final Logger LOG = LoggerFactory.getLogger(CarsController.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(PersonRepository.class);

    private CarRepository carRepository;
    private CommentRepository commentRepository;
    private PersonRepository userRepository;
    private CarImageStorageService carImageStorageService;

    public CarsController(CarRepository carRepository, CommentRepository commentRepository,
                          PersonRepository userRepository, CarImageStorageService carImageStorageService) {
        this.carRepository = carRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.carImageStorageService = carImageStorageService;
    }

    @GetMapping("/")
    public String showSearch(Model model) {
        model.addAttribute("cars", carRepository.getAll());
        return "cars";
    }

    @GetMapping(value = "/api/cars/search", produces = "application/json")
    @ResponseBody
    public List<Car> search(@RequestParam("query") String query) throws SQLException {
        return carRepository.search(query);
    }

    @GetMapping("/cars")
    public String showCar(@RequestParam(name = "id", required = false) String id, Model model) {
        if (id == null) {
            model.addAttribute("cars", carRepository.getAll());
            return "cars";
        }

        model.addAttribute("car", carRepository.findById(id));
        List<Comment> comments = commentRepository.getAll(id);

        List<ViewComment> commentList = new ArrayList<>();

        for (Comment comment : comments) {
            Person person = userRepository.get(comment.getUserId());
            commentList.add(new ViewComment(
                    person.getFirstName() + " " + person.getLastName(),
                    comment.getComment(),
                    comment.getImagePath()));
        }

        model.addAttribute("comments", commentList);

        return "car";
    }

    @GetMapping("/comment-images/{fileName:.+}")
    @ResponseBody
    public ResponseEntity<Resource> showCommentImage(@PathVariable String fileName) {
        return ResponseEntity.ok(carImageStorageService.loadForDisplay(fileName));
    }

    @PostMapping("/cars/{id}")
    public String editCar(@PathVariable("id") int id, Car car) throws SQLException {
        carRepository.update(id, car);
        return "redirect:/cars?id=" + id;
    }

    @GetMapping("/buy-car/{id}")
    public String showBuyCar(
            @PathVariable("id") int id,
            @RequestParam(required = false) boolean addressError,
            @RequestParam(required = false) boolean bought,
            Model model) {

        model.addAttribute("id", id);

        if (addressError) {
            model.addAttribute("addressError", true);
        } else if (bought) {
            model.addAttribute("bought", true);
        }

        return "buy-car";
    }

    @PostMapping("/buy-car/{id}")
    public String buyCar(@PathVariable("id") int id, Address address, Model model) {
        if (address.getAddress().length() < 10) {
            return String.format("redirect:/buy-car/%s?addressError=true", id);
        }

        return String.format("redirect:/buy-car/%s?bought=true", id);
    }
}
