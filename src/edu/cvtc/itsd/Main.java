package edu.cvtc.itsd;

// Import statements //////////////////////////////////////////////////////////
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.TimerTask;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;

// CiCo application's primary class ///////////////////////////////////////////
public class Main {

  // Constants ////////////////////////////////////////////////////////////////
  private static final int VERSION = 100;

  private static final String CARD_MAIN = "Main";
  private static final String CARD_STATE = "State";
  private static final String CARD_ERROR = "Error";

  private static final int ERROR_UNKNOWN = 0;
  private static final int ERROR_NO_DB = 1;
  private static final int ERROR_NOT_FOUND = 2;
  private static final int ERROR_UPDATE_FAILED = 3;
  private static final int ERROR_INSERT_FAILED = 4;

  private static final long TIMEOUT_PANEL_MS = 10 * 1000;
  private static final int TIMEOUT_STATEMENT_S = 5;

  // Internal classes ///////////////////////////////////////////////////////////

  private static class InputFilter extends DocumentFilter {
    private static final int MAX_LENGTH = 8;

    @Override
    public void insertString(FilterBypass fb, int offset, String stringToAdd, AttributeSet attr)
        throws BadLocationException {

      if (fb.getDocument().getLength() + stringToAdd.length() <= MAX_LENGTH
          && stringToAdd.matches("\\d+")) {
        super.insertString(fb, offset, stringToAdd, attr);
      } else {
        Toolkit.getDefaultToolkit().beep();
      }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int lengthToDelete, String stringToAdd,
        AttributeSet attr) throws BadLocationException {

      if ((fb.getDocument().getLength() - lengthToDelete + stringToAdd.length()) <= MAX_LENGTH
          && stringToAdd.matches("\\d*")) {
        super.replace(fb, offset, lengthToDelete, stringToAdd, attr);
      } else {
        Toolkit.getDefaultToolkit().beep();
      }
    }
  }

  public static class Update implements ActionListener {
    public void actionPerformed(ActionEvent evt) {
      Main.processCard();
    }
  }

  public static class Handler implements ActionListener {
    public void actionPerformed(ActionEvent evt) {
      Main.doneProcessing();
    }
  }

  public static class Timeout extends TimerTask {
    public void run() {
      Main.doneProcessing();
    }
  }

  public static class OnShutdown implements Runnable {
    public void run() {
      try {
        statementQueryCard.close();
        statementUpdateMember.close();
        statementUpdateLog.close();
        db.close();
        System.out.println("Clean shutdown");
      } catch (SQLException e) {
        System.err.println(e.getMessage());
      }
    }
  }

  // GUI variables ////////////////////////////////////////////////////////////

  static JPanel deck;
  static JTextField fieldNumber;
  static JLabel labelReason;
  static JLabel labelUser;
  static JLabel labelState;
  static JButton buttonAcknowledge;

  // Timer variables //////////////////////////////////////////////////////////

  static java.util.Timer timer;
  static Timeout timeout;

  // Database variables ///////////////////////////////////////////////////////

  static Connection db;
  static PreparedStatement statementQueryCard;
  static PreparedStatement statementUpdateMember;
  static PreparedStatement statementUpdateLog;

  // Process card /////////////////////////////////////////////////////////////

  private static void processCard() {

    if (db == null) {
      showError(ERROR_NO_DB);
      return;
    }

    ResultSet rows = null;

    try {

      statementQueryCard.setString(1, fieldNumber.getText());
      rows = statementQueryCard.executeQuery();

      if (rows.next()) {

        int id = rows.getInt("id");
        String name = rows.getString("name");
        int currentState = rows.getInt("is_checked_in");

        currentState = (currentState + 1) % 2;

        statementUpdateMember.setInt(1, currentState);
        statementUpdateMember.setInt(2, id);

        int numChanged = statementUpdateMember.executeUpdate();

        if (numChanged != 1) {
          showError(ERROR_UPDATE_FAILED);
          return;
        }

        statementUpdateLog.setInt(1, id);
        statementUpdateLog.setInt(2, currentState);

        int numInserted = statementUpdateLog.executeUpdate();

        if (numInserted != 1) {
          showError(ERROR_INSERT_FAILED);
        }

        updateStateLabels(name, currentState == 1);
        scheduleTransitionFrom(CARD_STATE, null);

      } else {
        showError(ERROR_NOT_FOUND);
      }

    } catch (SQLException e) {

      System.err.println(e.getMessage());
      showError(ERROR_UNKNOWN);

    } finally {

      try {
        if (rows != null) rows.close();
      } catch (SQLException e2) {
        System.err.println(e2.getMessage());
      }

    }
  }

  // Show errors //////////////////////////////////////////////////////////////

  private static void showError(int code) {

    String[] explanations = {
        "Please inform staff an unknown error occurred.",
        "Please inform staff that database wasn't found.",
        "Please show your card to staff to validate.",
        "Please inform staff that status updates failed.",
        "Please inform staff that log updates failed."
    };

    labelReason.setText(explanations[code]);
    scheduleTransitionFrom(CARD_ERROR, buttonAcknowledge);
  }

  // Panel transition /////////////////////////////////////////////////////////

  private static void scheduleTransitionFrom(String fromCard, JButton toFocus) {

    if (timeout != null) {
      timeout.cancel();
    }

    timeout = new Timeout();
    timer.schedule(timeout, TIMEOUT_PANEL_MS);

    ((CardLayout) deck.getLayout()).show(deck, fromCard);

    if (toFocus != null) {
      toFocus.grabFocus();
    }
  }

  // Done processing //////////////////////////////////////////////////////////

  private static void doneProcessing() {

    timeout.cancel();
    timeout = null;

    fieldNumber.setText("");

    ((CardLayout) deck.getLayout()).show(deck, CARD_MAIN);

    fieldNumber.grabFocus();
  }

  // Update labels ////////////////////////////////////////////////////////////

  private static void updateStateLabels(String name, boolean isCheckedInNow) {

    labelUser.setText(name);
    labelState.setText(isCheckedInNow ? "Checked IN" : "Checked OUT");
  }

  // Entry point //////////////////////////////////////////////////////////////

  public static void main(String[] args) {

    db = null;
    timer = new java.util.Timer("CiCo timeout timer");
    Handler handler = new Handler();

    JFrame frame = new JFrame();

    // Ticket 502 fix
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    frame.setMinimumSize(new Dimension(320, 240));
    frame.setPreferredSize(new Dimension(640, 480));
    frame.setMaximumSize(new Dimension(640, 480));

    deck = new JPanel(new CardLayout());

    Font fontMain = new Font(Font.SANS_SERIF, Font.PLAIN, 24);

    JPanel panelMain = new JPanel();
    panelMain.setLayout(new BoxLayout(panelMain, BoxLayout.PAGE_AXIS));
    panelMain.setBackground(Color.black);

    panelMain.add(Box.createVerticalGlue());

    JLabel labelDirective = new JLabel("Scan card", JLabel.LEADING);
    labelDirective.setFont(fontMain);
    labelDirective.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelDirective.setForeground(Color.cyan);
    panelMain.add(labelDirective);

    fieldNumber = new JTextField();

    InputFilter filter = new InputFilter();
    ((AbstractDocument) (fieldNumber.getDocument())).setDocumentFilter(filter);

    fieldNumber.setPreferredSize(new Dimension(200, 32));
    fieldNumber.setMaximumSize(new Dimension(200, 32));
    fieldNumber.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    fieldNumber.setBackground(Color.green);
    fieldNumber.setForeground(Color.magenta);

    panelMain.add(fieldNumber);

    JButton updateButton = new JButton("Update");
    updateButton.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    updateButton.addActionListener(new Update());
    panelMain.add(updateButton);

    panelMain.add(Box.createVerticalGlue());

    JPanel panelStatus = new JPanel();
    panelStatus.setLayout(new BoxLayout(panelStatus, BoxLayout.PAGE_AXIS));
    panelStatus.setBackground(Color.blue);

    panelStatus.add(Box.createVerticalGlue());

    labelUser = new JLabel("Registrant", JLabel.LEADING);
    labelUser.setFont(fontMain);
    labelUser.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelUser.setForeground(Color.yellow);

    panelStatus.add(labelUser);

    labelState = new JLabel("updated", JLabel.LEADING);
    labelState.setFont(fontMain);
    labelState.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelState.setForeground(Color.magenta);

    panelStatus.add(labelState);

    panelStatus.add(Box.createVerticalGlue());

    JPanel panelError = new JPanel();
    panelError.setLayout(new BoxLayout(panelError, BoxLayout.PAGE_AXIS));
    panelError.setBackground(Color.red);

    panelError.add(Box.createVerticalGlue());

    labelReason = new JLabel("", JLabel.LEADING);
    labelReason.setFont(fontMain);
    labelReason.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelReason.setForeground(Color.yellow);

    panelError.add(labelReason);

    buttonAcknowledge = new JButton("OK");
    buttonAcknowledge.addActionListener(handler);
    buttonAcknowledge.setAlignmentX(JComponent.CENTER_ALIGNMENT);

    panelError.add(buttonAcknowledge);
    panelError.add(Box.createVerticalGlue());

    deck.add(panelMain, CARD_MAIN);
    deck.add(panelStatus, CARD_STATE);
    deck.add(panelError, CARD_ERROR);

    frame.getContentPane().add(deck, BorderLayout.CENTER);

    JLabel labelMeta = new JLabel("CiCo v" + VERSION);
    labelMeta.setOpaque(true);
    labelMeta.setBackground(Color.darkGray);
    labelMeta.setForeground(Color.white);
    labelMeta.setBorder(new EmptyBorder(10, 10, 10, 10));

    frame.getContentPane().add(labelMeta, BorderLayout.PAGE_END);

    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}