package edu.cvtc.itsd;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

public class InputFilter extends DocumentFilter {

    private final int maxLength;

    public InputFilter(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
            throws BadLocationException {

        if (text == null) return;

        Document doc = fb.getDocument();
        String current = doc.getText(0, doc.getLength());

        String candidate = new StringBuilder(current).insert(offset, text).toString();

        if (isValid(candidate)) {
            super.insertString(fb, offset, text, attr);
        }
        // else: ignore invalid input
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {

        if (text == null) return;

        Document doc = fb.getDocument();
        String current = doc.getText(0, doc.getLength());

        StringBuilder sb = new StringBuilder(current);
        sb.replace(offset, offset + length, text);
        String candidate = sb.toString();

        if (isValid(candidate)) {
            super.replace(fb, offset, length, text, attrs);
        }
        // else: ignore invalid input
    }

    private boolean isValid(String candidate) {
        // allow empty while typing/backspacing
        if (candidate.isEmpty()) return true;

        // must be digits only AND not exceed max length
        if (candidate.length() > maxLength) return false;

        for (int i = 0; i < candidate.length(); i++) {
            if (!Character.isDigit(candidate.charAt(i))) return false;
        }
        return true;
    }
}