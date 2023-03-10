package com.samsara.paladin.service.user;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samsara.paladin.dto.ResetPasswordDetails;
import com.samsara.paladin.dto.UserDto;
import com.samsara.paladin.enums.EventAction;
import com.samsara.paladin.enums.EventCategory;
import com.samsara.paladin.enums.RoleName;
import com.samsara.paladin.events.CustomEventPublisher;
import com.samsara.paladin.exceptions.passwordValidation.IllegalPasswordArgumentException;
import com.samsara.paladin.exceptions.passwordValidation.PasswordArgumentMissingException;
import com.samsara.paladin.exceptions.passwordValidation.ResetPasswordFailedException;
import com.samsara.paladin.exceptions.user.EmailExistsException;
import com.samsara.paladin.exceptions.user.EmailNotFoundException;
import com.samsara.paladin.exceptions.user.UserNotFoundException;
import com.samsara.paladin.exceptions.user.UsernameExistsException;
import com.samsara.paladin.exceptions.user.UsernameNotFoundException;
import com.samsara.paladin.model.Role;
import com.samsara.paladin.model.User;
import com.samsara.paladin.repository.RoleRepository;
import com.samsara.paladin.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private CustomEventPublisher customEventPublisher;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Set<UserDto> searchUsers(String searchTerm) {
        Set<User> results = new HashSet<>();
        results.addAll(userRepository.searchByUsername(searchTerm));
        results.addAll(userRepository.searchByName(searchTerm));
        results.addAll(userRepository.searchByEmail(searchTerm));
        return results
                .stream()
                .map(this::convertUserToDto)
                .collect(Collectors.toSet());
    }

    public UserDto registerUser(UserDto userDto) {
        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new UsernameExistsException("Account with username '" + userDto.getUsername() + "' already exist");
        }
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new EmailExistsException("Account with email '" + userDto.getEmail() + "' already exist!");
        }
        if (userDto.getPassword() == null) {
            throw new PasswordArgumentMissingException("Password missing for new user!");
        }
        userDto.setCreationDate(new Date());
        userDto.setEnabled(true);
        User user = convertUserToEntity(userDto);
        encryptUserPassword(user);
        assignDefaultRoleToUser(user);
        User registeredUser = userRepository.save(user);
        publishUserEvent(registeredUser.getUsername(), EventAction.REGISTER);
        return convertUserToDto(registeredUser);
    }

    @Override
    public UserDto updateUser(UserDto userDto) {
        Optional<User> optionalUser = userRepository.findByUsername(userDto.getUsername());
        if (optionalUser.isEmpty()) {
            throw new UsernameNotFoundException("Username '" + userDto.getUsername() + "' not found!");
        }
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new EmailExistsException("Account with email '" + userDto.getEmail() + "' already exist!");
        }
        if (userDto.getPassword() != null) {
            throw new IllegalPasswordArgumentException("Please use 'password reset' option for password update!");
        }
        User currentUser = optionalUser.get();
        modelMapper.map(userDto, currentUser);
        User updatedUser = userRepository.save(currentUser);
        publishUserEvent(updatedUser.getUsername(), EventAction.EDIT);
        return convertUserToDto(updatedUser);
    }

    @Override
    public boolean resetUserPassword(ResetPasswordDetails resetPasswordDetails) {
        userRepository.findByUsername(resetPasswordDetails.getUsername())
                .filter(user -> user.getSecretAnswer().equals(resetPasswordDetails.getSecretAnswer()))
                .map(user -> encryptUserPassword(user, resetPasswordDetails.getNewPassword()))
                .map(userRepository::save)
                .orElseThrow(
                        () -> new ResetPasswordFailedException("Password reset failed! Invalid data!")
                );
        publishUserEvent(resetPasswordDetails.getUsername(), EventAction.CHANGE_PASSWORD);
        return true;
    }

    @Override
    public UserDto grantAdminRoleToUser(String username) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            throw new UsernameNotFoundException("Username '" + username + "' not found!");
        }
        User currentUser = optionalUser.get();
        currentUser.getRoles().add(roleRepository.findByName(RoleName.ADMIN));
        User adminUser = userRepository.save(currentUser);
        publishUserEvent(adminUser.getUsername(), EventAction.GRANT_ADMIN);
        return convertUserToDto(adminUser);
    }

    @Override
    public void deleteUser(String username) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            throw new UsernameNotFoundException("Username '" + username + "' not found!");
        }
        User currentUser = optionalUser.get();
        userRepository.delete(currentUser);
        publishUserEvent(currentUser.getUsername(), EventAction.DELETE);
    }

    @Override
    public List<UserDto> loadAllUsers() {
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            throw new UserNotFoundException("There are no users in the database!");
        }
        return convertUsersToDtoList(users);
    }

    @Override
    public UserDto loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::convertUserToDto)
                .orElseThrow(
                        () -> new UsernameNotFoundException(String.format("Username '%s' not found!", username))
                );
    }

    @Override
    public UserDto loadUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::convertUserToDto)
                .orElseThrow(
                        () -> new EmailNotFoundException(String.format("Email '%s' not found!", email))
                );
    }

    @Override
    public List<UserDto> loadUsersByFirstName(String firstName) {
        List<User> users = userRepository.findByFirstName(firstName);
        if (users.isEmpty()) {
            throw new UserNotFoundException("There is no user with first name '" + firstName + "'");
        }
        return convertUsersToDtoList(users);
    }

    @Override
    public List<UserDto> loadUsersByLastName(String lastName) {
        List<User> users = userRepository.findByLastName(lastName);
        if (users.isEmpty()) {
            throw new UserNotFoundException("There is no user with last name '" + lastName + "'");
        }
        return convertUsersToDtoList(users);
    }

    @Override
    public List<UserDto> loadFirst10AddedUsers() {
        List<User> users = userRepository.findFirst10ByOrderByCreationDateAsc();
        if (users.isEmpty()) {
            throw new UserNotFoundException("There are no users stored in the database!");
        }
        return convertUsersToDtoList(users);
    }

    @Override
    public List<UserDto> loadLast10AddedUsers() {
        List<User> users = userRepository.findFirst10ByOrderByCreationDateDesc();
        if (users.isEmpty()) {
            throw new UserNotFoundException("There are no users in the database!");
        }
        return convertUsersToDtoList(users);
    }

    @Override
    public List<UserDto> loadEnabledUsers() {
        List<User> users = userRepository.findByEnabled(true);
        if (users.isEmpty()) {
            throw new UserNotFoundException("There are no enabled users!");
        }
        return convertUsersToDtoList(users);
    }

    @Override
    public List<UserDto> loadAdmins() {
        List<User> users = userRepository.findAdmins();
        if (users.isEmpty()) {
            throw new UserNotFoundException("There are no admins in the database!");
        }
        return convertUsersToDtoList(users);
    }

    private void encryptUserPassword(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
    }

    private User encryptUserPassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        return user;
    }

    private void assignDefaultRoleToUser(User user) {
        Role userRole = roleRepository.findByName(RoleName.USER);
        user.setRoles(Collections.singleton(userRole));
    }

    private void publishUserEvent(String username, EventAction action) {
        customEventPublisher.publishEvent(
                EventCategory.USER,
                username,
                action
        );
    }

    private List<UserDto> convertUsersToDtoList(List<User> users) {
        return users
                .stream()
                .map(this::convertUserToDto)
                .collect(Collectors.toList());
    }

    private UserDto convertUserToDto(User user) {
        UserDto userDto = modelMapper.map(user, UserDto.class);
        userDto.setHeroCount(user.getHeroes().size());
        return userDto;
    }

    private User convertUserToEntity(UserDto userDto) {
        return modelMapper.map(userDto, User.class);
    }
}
