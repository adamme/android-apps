/* 
function processHelloFreshEdit(e) {
  // 1. Stop if run manually (e is undefined)
  if (!e || !e.range) return;
  
  var range = e.range;
  
  // 2. Only trigger for Column B (2), skip row 1 (header), and single cells only
  if (range.getColumn() !== 2 || range.getRow() === 1 || range.getNumRows() > 1 || range.getNumColumns() > 1) return;
  
  var mealName = range.getValue();
  
  // 3. Stop if the cell is blank or not text
  if (!mealName || typeof mealName !== 'string') return;
  
  // 4. Stop if the cell already has a hyperlink
  if (range.getRichTextValue().getLinkUrl()) return;
  
  // 5. Create a direct link to HelloFresh's internal recipe search
  var recipeUrl = "https://www.hellofresh.com/recipes/search?q=" + encodeURIComponent(mealName);
  
  // 6. Convert the text into a clickable link
  var richText = SpreadsheetApp.newRichTextValue()
    .setText(mealName)
    .setLinkUrl(recipeUrl)
    .build();
    
  range.setRichTextValue(richText);
}

*/
