function onEdit(e) {
  // Ensure the event object exists to prevent errors if run manually
  if (!e) return;

  const range = e.range;
  const sheet = range.getSheet();

  // Check if the edited cell is exactly B2 (Row 2, Column 2)
  if (range.getRow() === 2 && range.getColumn() === 2) {
    
    // Get the current value of the edited cell
    const cellValue = range.getValue();

    // If the cell is not empty, insert a new row above B2 (at row 2)
    if (cellValue !== "") {
      sheet.insertRowBefore(2);
    }
  }
}
