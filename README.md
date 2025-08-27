# 🎴 Set – Card Game

## 📝 Overview
**Set** is a classic logic card game where players must find **groups of three cards** ("sets") that follow specific mathematical rules.  
Each card is defined by four attributes:
1. **Shape** (e.g., oval, squiggle, diamond)  
2. **Color** (e.g., red, green, purple)  
3. **Number** (1, 2, or 3 symbols)  
4. **Shading** (empty, solid, striped)  

A valid set is a group of three cards such that, for each attribute:
- either all three cards are the same,  
- or all three cards are different.  

---

## 🎮 How to Play
1. Lay out 12 cards on the table.  
2. The goal is to find a "set" – a triple that satisfies the rule above.  
3. If no set exists on the table, deal 3 more cards.  
4. The game ends when the deck is empty and no sets remain.  

---

## 🛠️ This Project
This project implements the **Set card game** in Java (or whichever language you specify).  

### Features:
- 🎲 Generate the full deck with all possible cards.  
- 🔍 Automatically check if three cards form a valid set.  
- 🖥️ Text-based/graphical interface (depending on implementation).  
- 🧪 Unit tests to validate game logic.  

---
# there are two main modes:
1) Human players
2) Bot players
# you can change the setting in the config.properties file

## 🚀 Installation & Run
1. Clone the repository:  
   ```bash
   git clone https://github.com/<username>/set-card-game.git
   cd set-card-game
