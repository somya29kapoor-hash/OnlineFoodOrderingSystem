import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Scanner;
import java.util.Stack;

/**
 * Online Food Ordering System (PS No. 14)
 * Data Structures used:
 *   ArrayList  -> food menu
 *   Queue      -> pending orders (LinkedList, FIFO)
 *   Stack      -> undo last added item while placing an order
 *   Insertion Sort (manual) -> sort menu by price
 *   File handling -> orders.txt
 */
public class FoodOrderingSystem {

    // ===================== INNER CLASSES =====================

    /** One item on the food menu. */
    static class MenuItem {
        private final String id;
        private final String name;
        private final int price;

        MenuItem(String id, String name, int price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        String getId() { return id; }
        String getName() { return name; }
        int getPrice() { return price; }
    }

    /** One line of an order: a menu item and how many of it were ordered. */
    static class OrderItem {
        private final MenuItem menuItem;
        private final int quantity;

        OrderItem(MenuItem menuItem, int quantity) {
            this.menuItem = menuItem;
            this.quantity = quantity;
        }

        MenuItem getMenuItem() { return menuItem; }
        int getQuantity() { return quantity; }

        long getSubtotal() {
            return (long) menuItem.getPrice() * quantity;
        }
    }

    /** A customer order. */
    static class Order {
        private final String orderId;
        private final String customerName;
        private final List<OrderItem> items;
        private boolean prepared; // false = pending, true = prepared

        Order(String orderId, String customerName, List<OrderItem> items, boolean prepared) {
            this.orderId = orderId;
            this.customerName = customerName;
            this.items = items;
            this.prepared = prepared;
        }

        String getOrderId() { return orderId; }
        String getCustomerName() { return customerName; }
        List<OrderItem> getItems() { return items; }
        boolean isPrepared() { return prepared; }
        void markPrepared() { prepared = true; }

        long getTotal() {
            long total = 0;
            for (OrderItem item : items) {
                total += item.getSubtotal();
            }
            return total;
        }

        /** Example: "Veg Burger x2, Cold Coffee x1" */
        String getItemsText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(items.get(i).getMenuItem().getName())
                  .append(" x").append(items.get(i).getQuantity());
            }
            return sb.toString();
        }
    }

    // ===================== DATA (static fields) =====================

    private static final String FILE_NAME = "orders.txt";

    // ArrayList: stores all food menu items (grows/shrinks dynamically)
    private static final ArrayList<MenuItem> menu = new ArrayList<>();

    // Queue (implemented by LinkedList): pending orders, FIFO
    private static final Queue<Order> pendingOrders = new LinkedList<>();

    // Every order ever placed (pending + prepared). Needed so we can save
    // everything to the file and keep the order ID counter correct.
    private static final ArrayList<Order> allOrders = new ArrayList<>();

    // Number used for the next order ID (O101, O102, ...)
    private static int nextOrderNumber = 101;

    private static final Scanner scanner = new Scanner(System.in);

    // ===================== MAIN =====================

    public static void main(String[] args) {
        loadMenu();
        loadOrdersFromFile();

        boolean running = true;
        try {
            while (running) {
                showMainMenu();
                int choice = readInt("Enter your choice: ");
                switch (choice) {
                    case 1: viewMenu(); break;
                    case 2: placeOrder(); break;
                    case 3: processNextOrder(); break;
                    case 4: viewPendingOrders(); break;
                    case 5: sortMenuByPrice(); break;
                    case 6: running = false; break;
                    default: System.out.println("Invalid choice. Please enter 1 to 6.");
                }
            }
        } catch (NoSuchElementException e) {
            // Input ended unexpectedly (e.g. Ctrl+D); still exit safely and save.
            System.out.println();
            System.out.println("Input closed. Exiting...");
        }

        saveOrdersToFile();
        scanner.close(); // close the input resource
        System.out.println("Orders saved to " + FILE_NAME + ". Thank you! Goodbye.");
    }

    private static void showMainMenu() {
        System.out.println();
        System.out.println("===== ONLINE FOOD ORDERING SYSTEM =====");
        System.out.println();
        System.out.println("1. View Food Menu");
        System.out.println("2. Place Order");
        System.out.println("3. Process Next Order");
        System.out.println("4. View Pending Orders");
        System.out.println("5. Sort Menu by Price");
        System.out.println("6. Exit");
        System.out.println();
    }

    // ===================== MENU =====================

    /** Fills the ArrayList with the three required menu items. */
    private static void loadMenu() {
        menu.add(new MenuItem("M101", "Veg Burger", 120));
        menu.add(new MenuItem("M102", "Paneer Wrap", 150));
        menu.add(new MenuItem("M103", "Cold Coffee", 90));
    }

    /** Option 1: show the menu in its current order. */
    private static void viewMenu() {
        System.out.println();
        System.out.println("--- FOOD MENU ---");
        System.out.println("Item ID | Food Name | Price");
        for (MenuItem item : menu) {
            System.out.println(item.getId() + " | " + item.getName() + " | " + item.getPrice());
        }
    }

    /** Searches the ArrayList for an item ID. Returns null if not found. */
    private static MenuItem findMenuItem(String itemId) {
        for (MenuItem item : menu) {
            if (item.getId().equalsIgnoreCase(itemId)) {
                return item;
            }
        }
        return null;
    }

    // ===================== PLACE ORDER =====================

    /** Option 2: build an order item by item, then add it to the queue. */
    private static void placeOrder() {
        String customerName = readNonEmptyText("Enter customer name: ");

        List<OrderItem> cart = new ArrayList<>(); // items of the current order
        Stack<OrderItem> undoStack = new Stack<>(); // remembers additions (last in, first out)

        boolean ordering = true;
        while (ordering) {
            String input = readLine("Enter Item ID (U = Undo last item, D = Done, C = Cancel): ")
                    .toUpperCase();

            if (input.equals("U")) {
                undoLastItem(cart, undoStack);
            } else if (input.equals("C")) {
                System.out.println("Order cancelled.");
                return;
            } else if (input.equals("D")) {
                if (cart.isEmpty()) {
                    System.out.println("Cannot place an empty order. Please add at least one item.");
                } else {
                    confirmOrder(customerName, cart);
                    ordering = false;
                }
            } else {
                addItemToCart(input, cart, undoStack);
            }
        }
    }

    /** Validates the item ID and quantity, adds the item and pushes it on the Stack. */
    private static void addItemToCart(String itemId, List<OrderItem> cart, Stack<OrderItem> undoStack) {
        MenuItem menuItem = findMenuItem(itemId);
        if (menuItem == null) {
            System.out.println("Item " + itemId + " not available.");
            return;
        }

        int quantity = readPositiveInt("Enter quantity: ");
        OrderItem orderItem = new OrderItem(menuItem, quantity);

        cart.add(orderItem);
        undoStack.push(orderItem); // Stack: remember this addition so it can be undone

        System.out.println("Added: " + menuItem.getName() + " x" + quantity
                + " (Subtotal: " + orderItem.getSubtotal() + ")");
        System.out.println("Current total: " + calculateTotal(cart));
    }

    /** Undo: pop the most recent addition from the Stack and remove it from the cart. */
    private static void undoLastItem(List<OrderItem> cart, Stack<OrderItem> undoStack) {
        if (undoStack.isEmpty()) { // handle empty stack
            System.out.println("Nothing to undo.");
            return;
        }
        OrderItem lastAdded = undoStack.pop();
        cart.remove(lastAdded);
        System.out.println("Removed: " + lastAdded.getMenuItem().getName()
                + " x" + lastAdded.getQuantity());
        System.out.println("Current total: " + calculateTotal(cart));
    }

    private static long calculateTotal(List<OrderItem> cart) {
        long total = 0;
        for (OrderItem item : cart) {
            total += item.getSubtotal();
        }
        return total;
    }

    /** Creates the order with the next ID and adds it to the Queue. */
    private static void confirmOrder(String customerName, List<OrderItem> cart) {
        String orderId = "O" + nextOrderNumber;
        nextOrderNumber++;

        Order order = new Order(orderId, customerName, cart, false);
        pendingOrders.add(order); // Queue: new order goes to the back
        allOrders.add(order);

        System.out.println("Order " + orderId + " placed.");
        System.out.println(customerName + " | " + order.getItemsText()
                + " | Total: " + order.getTotal());
    }

    // ===================== PROCESS / VIEW ORDERS =====================

    /** Option 3: remove the first order from the Queue (FIFO). */
    private static void processNextOrder() {
        if (pendingOrders.isEmpty()) {
            System.out.println("No pending orders.");
            return;
        }

        Order order = pendingOrders.poll(); // removes the order at the front
        order.markPrepared();
        System.out.println("Processing: " + order.getOrderId() + " | " + order.getCustomerName());
        System.out.println("Order " + order.getOrderId() + " prepared.");

        if (pendingOrders.isEmpty()) {
            System.out.println("No pending orders.");
        }
    }

    /** Option 4: show the Queue from front to back without removing anything. */
    private static void viewPendingOrders() {
        if (pendingOrders.isEmpty()) {
            System.out.println("No pending orders.");
            return;
        }

        int position = 1;
        for (Order order : pendingOrders) { // iterating does not remove elements
            System.out.println(position + ". " + order.getOrderId() + " | " + order.getCustomerName());
            position++;
        }
    }

    // ===================== INSERTION SORT =====================

    /** Option 5: sort the menu by price (ascending) using manual Insertion Sort. */
    private static void sortMenuByPrice() {
        insertionSortByPrice(menu);

        // Output format as given in the university test case 4(c).
        for (MenuItem item : menu) {
            System.out.println(item.getId() + " | " + item.getPrice());
        }
    }

    /**
     * Insertion Sort: take each element and insert it into the correct place
     * in the already sorted left part of the list.
     */
    private static void insertionSortByPrice(ArrayList<MenuItem> list) {
        for (int i = 1; i < list.size(); i++) {
            MenuItem current = list.get(i); // element to insert
            int j = i - 1;

            // shift larger-priced items one position to the right
            while (j >= 0 && list.get(j).getPrice() > current.getPrice()) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, current); // place the element in its correct position
        }
    }

    // ===================== FILE HANDLING =====================
    // File format (one order per line):
    // orderId|customerName|STATUS|total|itemId:qty;itemId:qty
    // Example: O101|Riya|PENDING|240|M101:2

    /** Runs when the program starts: reads orders.txt (if it exists). */
    private static void loadOrdersFromFile() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return; // first run: nothing to load, numbering starts at O101
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                Order order = parseOrder(line);
                if (order == null) {
                    System.out.println("Skipping invalid line in " + FILE_NAME + ": " + line);
                    continue;
                }
                allOrders.add(order);
                if (!order.isPrepared()) {
                    pendingOrders.add(order); // restore pending orders into the Queue (file order = FIFO)
                }
                // keep the next order ID correct
                int number = Integer.parseInt(order.getOrderId().substring(1));
                if (number >= nextOrderNumber) {
                    nextOrderNumber = number + 1;
                }
            }
        } catch (IOException e) {
            System.out.println("Could not read " + FILE_NAME + ": " + e.getMessage());
        }
    }

    /** Converts one line of the file into an Order. Returns null if the line is bad. */
    private static Order parseOrder(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5 || !parts[0].matches("O\\d+")) {
                return null;
            }

            List<OrderItem> items = new ArrayList<>();
            for (String itemText : parts[4].split(";")) {
                String[] pair = itemText.split(":");
                MenuItem menuItem = findMenuItem(pair[0]);
                int quantity = Integer.parseInt(pair[1]);
                if (menuItem == null || quantity <= 0) {
                    return null;
                }
                items.add(new OrderItem(menuItem, quantity));
            }

            boolean prepared = parts[2].equals("PREPARED");
            return new Order(parts[0], parts[1], items, prepared);
        } catch (RuntimeException e) { // NumberFormatException, ArrayIndexOutOfBounds, ...
            return null;
        }
    }

    /** Runs on Exit: writes every order to orders.txt. */
    private static void saveOrdersToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME))) {
            for (Order order : allOrders) {
                StringBuilder itemsText = new StringBuilder();
                for (OrderItem item : order.getItems()) {
                    if (itemsText.length() > 0) {
                        itemsText.append(";");
                    }
                    itemsText.append(item.getMenuItem().getId()).append(":").append(item.getQuantity());
                }
                String status = order.isPrepared() ? "PREPARED" : "PENDING";
                writer.write(order.getOrderId() + "|" + order.getCustomerName() + "|" + status
                        + "|" + order.getTotal() + "|" + itemsText);
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Could not save " + FILE_NAME + ": " + e.getMessage());
        }
    }

    // ===================== INPUT HELPERS =====================

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /** Reads an int; keeps asking until the user types a valid number. */
    private static int readInt(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    /** Reads a positive whole number; rejects zero, negatives and text. */
    private static int readPositiveInt(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                int value = Integer.parseInt(text);
                if (value > 0) {
                    return value;
                }
                System.out.println("Quantity must be greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid quantity. Enter a positive whole number.");
            }
        }
    }

    /** Reads non-empty text. The '|' character is removed because it is the file separator. */
    private static String readNonEmptyText(String prompt) {
        while (true) {
            String text = readLine(prompt).replace("|", " ").trim();
            if (!text.isEmpty()) {
                return text;
            }
            System.out.println("Input cannot be empty.");
        }
    }
}