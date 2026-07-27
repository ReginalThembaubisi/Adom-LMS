# Learner Assignments Management System

A modern, full-stack ed-tech application featuring a Spring Boot backend, React frontend, and MySQL database, styled with the **Orbital** design system (glassmorphism cards, slate-indigo gradients, and Outfit typography).

## ✨ Features
* **Student Portal**: submit assignments, view resources, and monitor task status.
* **Lecturer Dashboard**: publish learning resources, open assignment slots, and grade submitted files.
* **Admin Dashboard**: manage student registrations and control application switches.
* **Orbital Glassmorphism**: centered entry cards with dynamic light-blue contrast, frosted blurs, and background gradient elements.

---

## 🛠️ Technology Stack
* **Backend**: Java 17, Spring Boot, Spring Security, Spring Data JPA, Hibernate, MySQL.
* **Frontend**: React (Vite), CSS3 (Custom Variables), Google Fonts (Outfit).
* **Build Tools**: Maven.

---

## 🚀 Getting Started

### Prerequisites
* Java 17 or higher
* Node.js & npm
* MySQL Database

### Database Setup
Create a database named `learner_assignments` in MySQL:
```sql
CREATE DATABASE learner_assignments;
```
Configure your database credentials in `src/main/resources/application.properties`.

### Running the Application

1. **Build the Frontend Assets**:
   ```bash
   cd frontend
   npm install
   npm run build
   ```

2. **Run the Spring Boot Backend**:
   From the project root directory, run:
   ```bash
   mvn spring-boot:run
   ```
   The application will start on `http://localhost:8080`.
