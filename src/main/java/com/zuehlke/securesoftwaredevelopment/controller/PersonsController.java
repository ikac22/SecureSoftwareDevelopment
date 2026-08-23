package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.PersonRepository;
import com.zuehlke.securesoftwaredevelopment.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@Controller
public class PersonsController {

    private static final Logger LOG = LoggerFactory.getLogger(PersonsController.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(PersonRepository.class);

    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    public PersonsController(PersonRepository personRepository, UserRepository userRepository) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/persons/{id}")
    public String person(@PathVariable int id, Model model) {
        model.addAttribute("person", personRepository.get(id));
        model.addAttribute("editableProfile", false);
        model.addAttribute("directoryManaged", false);
        return "person";
    }

    @GetMapping("/myprofile")
    public String self(Model model, Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();

        if (principal instanceof User) {
            User user = (User) principal;
            Person person = personRepository.get(user.getId());
            if (person == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found");
            }
            model.addAttribute("person", person);
            model.addAttribute("editableProfile", true);
            model.addAttribute("directoryManaged", false);
        } else {
            model.addAttribute("person", null);
            model.addAttribute("editableProfile", false);
            model.addAttribute("directoryManaged", true);
            model.addAttribute("accountName", authentication == null ? "" : authentication.getName());
        }

        return "person";
    }

    @DeleteMapping("/persons/{id}")
    public ResponseEntity<Void> person(@PathVariable int id) {
        personRepository.delete(id);
        userRepository.delete(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update-person")
    public String updatePerson(Person person, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Directory-managed employee profiles cannot be edited here");
        }

        User customer = (User) authentication.getPrincipal();
        if (person.getId() != customer.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Customers can edit only their own profile");
        }

        personRepository.update(person);
        return "redirect:/myprofile";
    }

    @GetMapping("/persons")
    public String persons(Model model) {
        model.addAttribute("persons", personRepository.getAll());
        return "persons";
    }

    @GetMapping(value = "/persons/search", produces = "application/json")
    @ResponseBody
    public List<Person> searchPersons(@RequestParam String searchTerm) throws SQLException {
        return personRepository.search(searchTerm);
    }
}
