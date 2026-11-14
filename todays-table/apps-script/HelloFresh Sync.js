/**
 * HelloFresh Upcoming Meals Sync
 * Uses HELLOFRESH_REFRESH_TOKEN in Script Properties to obtain a fresh Bearer token
 * (and automatically saves the rotated refresh_token back to Script Properties),
 * fetches the next active ("RUNNING") delivery week for your subscription,
 * reads the selected meals from /gw/my-deliveries/menu, checks them against the
 * HelloFresh tab in the Google Sheet, and inserts any new unique meals at the top.
 */
const HELLOFRESH_BASE_GW_URL = "https://www.hellofresh.com/gw";
const HELLOFRESH_DEFAULT_SUBSCRIPTION_ID = ""; // Set HELLOFRESH_SUBSCRIPTION_ID in Script Properties

function syncHelloFreshUpcomingMeals() {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const upcomingMeals = fetchHelloFreshUpcomingMeals();
    Logger.log("Upcoming meals on HelloFresh account: " + JSON.stringify(upcomingMeals));

    if (!upcomingMeals || upcomingMeals.length === 0) {
      Logger.log("No upcoming selected meals found on HelloFresh.");
      return { synced: 0, addedMeals: [], upcomingMeals: [] };
    }

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheet = getSheetForTab(ss, "HelloFresh");
    if (!sheet) {
      throw new Error("HelloFresh sheet tab not found.");
    }

    const lastRow = sheet.getLastRow();
    const existingMealNamesNormalized = new Set();
    const existingRecipeIds = new Set();

    if (lastRow >= 1) {
      const colBValues = sheet.getRange(1, 2, lastRow, 1).getDisplayValues();
      const colBRichText = sheet.getRange(1, 2, lastRow, 1).getRichTextValues();
      for (let i = 0; i < colBValues.length; i++) {
        const rawCell = colBValues[i][0];
        const normFull = normalizeMealName(rawCell);
        if (normFull && normFull !== "meal" && normFull !== "meals") {
          existingMealNamesNormalized.add(normFull);
          // Also index the base title before " with " or " in a " so both short and full titles match
          const baseTitle = normalizeMealName(String(rawCell).split(/\s+(?:with|in\s+a|plus)\s+/i)[0]);
          if (baseTitle) {
            existingMealNamesNormalized.add(baseTitle);
          }
        }
        const linkUrl = colBRichText[i][0] ? colBRichText[i][0].getLinkUrl() : null;
        if (linkUrl) {
          const idMatch = String(linkUrl).match(/-([a-f0-9]{24})(?:[/?#]|$)/i);
          if (idMatch && idMatch[1]) {
            existingRecipeIds.add(idMatch[1].toLowerCase());
          }
        }
      }
    }

    // Deduplicate upcoming meals and filter out any already present on the sheet
    const newUniqueMeals = [];
    for (const item of upcomingMeals) {
      const cleanName = (item.name || "").replace(/\s+/g, " ").trim();
      const fullName = (item.fullName || cleanName).replace(/\s+/g, " ").trim();
      const normShort = normalizeMealName(cleanName);
      const normFull = normalizeMealName(fullName);
      const recipeId = (item.id || "").toLowerCase();

      if (!normShort) continue;
      if (
        existingMealNamesNormalized.has(normShort) ||
        existingMealNamesNormalized.has(normFull) ||
        (recipeId && existingRecipeIds.has(recipeId))
      ) {
        continue;
      }

      existingMealNamesNormalized.add(normShort);
      existingMealNamesNormalized.add(normFull);
      if (recipeId) existingRecipeIds.add(recipeId);
      newUniqueMeals.push(item);
    }

    if (newUniqueMeals.length === 0) {
      Logger.log("All upcoming HelloFresh meals are already on the sheet.");
      return { synced: 0, addedMeals: [], upcomingMeals: upcomingMeals };
    }

    const topMealRow = findFirstMealRow(sheet);
    const maxCols = Math.max(sheet.getLastColumn(), 5);

    // Insert in reverse order at topMealRow so the top-to-bottom order matches the delivery menu
    for (let i = newUniqueMeals.length - 1; i >= 0; i--) {
      const mealItem = newUniqueMeals[i];
      sheet.insertRowBefore(topMealRow);

      // Copy formatting and Column E (Average) formula from the existing meal row (now at topMealRow + 1)
      if (sheet.getLastRow() >= topMealRow + 1) {
        const templateRowRange = sheet.getRange(topMealRow + 1, 1, 1, maxCols);
        const targetRowRange = sheet.getRange(topMealRow, 1, 1, maxCols);
        templateRowRange.copyTo(targetRowRange, SpreadsheetApp.CopyPasteType.PASTE_FORMAT, false);

        const templateAverageCell = sheet.getRange(topMealRow + 1, 5);
        const templateFormula = templateAverageCell.getFormula();
        if (templateFormula) {
          templateAverageCell.copyTo(
            sheet.getRange(topMealRow, 5),
            SpreadsheetApp.CopyPasteType.PASTE_FORMULA,
            false
          );
          if (/^=\s*ARRAYFORMULA/i.test(templateFormula)) {
            templateAverageCell.clearContent();
          }
        }
      }

      // Clear Date (Col A), Anissa (Col C), Adamme (Col D) and write Meal Name + Recipe Link in Col B
      sheet.getRange(topMealRow, 1).clearContent();
      const mealCell = sheet.getRange(topMealRow, 2);
      if (mealItem.recipeUrl) {
        const richText = SpreadsheetApp.newRichTextValue()
          .setText(mealItem.name)
          .setLinkUrl(mealItem.recipeUrl)
          .build();
        mealCell.setRichTextValue(richText);
      } else {
        mealCell.setValue(mealItem.name);
      }
      sheet.getRange(topMealRow, 3).clearContent();
      sheet.getRange(topMealRow, 4).clearContent();
    }

    const addedNames = newUniqueMeals.map(function (m) {
      return m.name;
    });
    Logger.log(
      "Added " +
        addedNames.length +
        " new HelloFresh meal(s) to top of sheet (row " +
        topMealRow +
        "): " +
        JSON.stringify(addedNames)
    );
    return { synced: addedNames.length, addedMeals: addedNames, upcomingMeals: upcomingMeals };
  } finally {
    lock.releaseLock();
  }
}

function fetchHelloFreshUpcomingMeals() {
  const scriptProps = PropertiesService.getScriptProperties();
  const subscriptionId =
    scriptProps.getProperty("HELLOFRESH_SUBSCRIPTION_ID") || HELLOFRESH_DEFAULT_SUBSCRIPTION_ID;
  const accessToken = getHelloFreshAccessToken(scriptProps);

  // 1. Fetch customer's upcoming deliveries to identify the next active ("RUNNING") delivery week
  const deliveriesUrl =
    HELLOFRESH_BASE_GW_URL + "/api/customers/me/deliveries?country=US&locale=en-US";
  const deliveriesResp = UrlFetchApp.fetch(deliveriesUrl, {
    method: "get",
    muteHttpExceptions: true,
    headers: {
      "User-Agent": BROWSER_USER_AGENT,
      "Authorization": "Bearer " + accessToken,
      "Accept": "application/json"
    }
  });

  if (deliveriesResp.getResponseCode() !== 200) {
    throw new Error(
      "HelloFresh deliveries request failed (HTTP " +
        deliveriesResp.getResponseCode() +
        "): " +
        deliveriesResp.getContentText()
    );
  }

  const deliveriesData = JSON.parse(deliveriesResp.getContentText());
  const items = deliveriesData.items || [];

  // Format today's date as YYYY-MM-DD in script timezone so we only check future deliveries
  const todayStr = Utilities.formatDate(
    new Date(),
    Session.getScriptTimeZone() || "America/New_York",
    "yyyy-MM-dd"
  );

  // Filter for active RUNNING deliveries in the future and sort ascending by deliveryDate
  const futureDeliveries = items
    .filter(function (item) {
      if (String(item.subscriptionId) !== String(subscriptionId)) return false;
      const state = String(item.state || item.status || "").toUpperCase();
      if (state !== "RUNNING" || item.deliveryBlocked) return false;
      const deliveryDate = String(item.deliveryDate || "").slice(0, 10);
      return deliveryDate >= todayStr;
    })
    .sort(function (a, b) {
      return String(a.deliveryDate || "").localeCompare(String(b.deliveryDate || ""));
    });

  // Grab ONLY the single immediate next future delivery week (no further weeks)
  const nextDelivery = futureDeliveries.length > 0 ? futureDeliveries[0] : null;

  if (!nextDelivery) {
    Logger.log("No upcoming RUNNING HelloFresh delivery found for subscription " + subscriptionId);
    return [];
  }

  const week = nextDelivery.id;
  const deliveryOption =
    (nextDelivery.deliveryOption && nextDelivery.deliveryOption.handle) || "US-1-0800-2000";
  const productSku =
    (nextDelivery.product && nextDelivery.product.handle) || "US-CBU-2-2-0";
  const servings =
    (nextDelivery.product &&
      nextDelivery.product.specs &&
      nextDelivery.product.specs.size) ||
    2;

  Logger.log(
    "Checking HelloFresh Next Delivery week=" +
      week +
      " (deliveryDate=" +
      nextDelivery.deliveryDate +
      ")"
  );

  // 2. Fetch the menu for that delivery week and filter for selected meals (selection.quantity > 0)
  const menuParams = {
    "delivery-option": deliveryOption,
    "locale": "en-US",
    "product-sku": productSku,
    "servings": String(servings),
    "subscription": String(subscriptionId),
    "week": week,
    "exclude-feedback": "true",
    "country": "US"
  };

  const queryString = Object.keys(menuParams)
    .map(function (k) {
      return encodeURIComponent(k) + "=" + encodeURIComponent(menuParams[k]);
    })
    .join("&");

  const menuUrl = HELLOFRESH_BASE_GW_URL + "/my-deliveries/menu?" + queryString;
  const menuResp = UrlFetchApp.fetch(menuUrl, {
    method: "get",
    muteHttpExceptions: true,
    headers: {
      "User-Agent": BROWSER_USER_AGENT,
      "Authorization": "Bearer " + accessToken,
      "Accept": "application/json",
      "X-Market-API-Version": "3"
    }
  });

  if (menuResp.getResponseCode() !== 200) {
    throw new Error(
      "HelloFresh menu request failed for week " +
        week +
        " (HTTP " +
        menuResp.getResponseCode() +
        "): " +
        menuResp.getContentText()
    );
  }

  const menuData = JSON.parse(menuResp.getContentText());
  const meals = menuData.meals || [];
  const addOnGroups =
    menuData.addOns && Array.isArray(menuData.addOns.groups)
      ? menuData.addOns.groups
      : Array.isArray(menuData.addOns)
      ? [{ addOns: menuData.addOns }]
      : [];

  const allEntries = [].concat(meals);
  for (let g = 0; g < addOnGroups.length; g++) {
    const groupAddOns = (addOnGroups[g] && addOnGroups[g].addOns) || [];
    for (let a = 0; a < groupAddOns.length; a++) {
      allEntries.push(groupAddOns[a]);
    }
  }

  const selectedMeals = [];
  const seenKeys = new Set();

  for (let i = 0; i < allEntries.length; i++) {
    const entry = allEntries[i];
    const quantity = getHelloFreshSelectedQuantity(entry);
    if (quantity > 0 && entry.recipe && entry.recipe.name) {
      const r = entry.recipe;
      const name = String(r.name).trim();
      const headline = r.headline ? String(r.headline).trim() : "";
      const fullName = headline ? name + " " + headline : name;
      const dedupeKey = (r.id ? String(r.id).toLowerCase() : "") || normalizeMealName(name);
      if (seenKeys.has(dedupeKey)) continue;
      seenKeys.add(dedupeKey);

      const recipeUrl = buildHelloFreshRecipeUrl(r);
      selectedMeals.push({
        id: r.id || "",
        name: name,
        fullName: fullName,
        recipeUrl: recipeUrl,
        imageUrl: r.image || null,
        quantity: quantity,
        week: week
      });
    }
  }

  return selectedMeals;
}

/**
 * Returns the selected quantity for a main meal or add-on/side/dessert entry.
 * Main meals use selection.quantity; add-ons/extras use selection.oneOffQuantity
 * or selection.preselectedQuantity (unless selection.skipped is true).
 */
function getHelloFreshSelectedQuantity(entry) {
  if (!entry || !entry.selection) return 0;
  const sel = entry.selection;
  if (sel.skipped === true) return 0;
  if (typeof sel.quantity === "number" && sel.quantity > 0) {
    return sel.quantity;
  }
  if (typeof sel.oneOffQuantity === "number" && sel.oneOffQuantity > 0) {
    return sel.oneOffQuantity;
  }
  if (typeof sel.preselectedQuantity === "number" && sel.preselectedQuantity > 0) {
    return sel.preselectedQuantity;
  }
  return 0;
}

function buildHelloFreshRecipeUrl(recipe) {
  if (!recipe) return null;
  if (recipe.websiteURL) {
    const rawUrl = String(recipe.websiteURL).trim();
    if (/^https?:\/\//i.test(rawUrl)) {
      return rawUrl;
    }
    if (rawUrl.startsWith("/")) {
      return "https://www.hellofresh.com" + rawUrl;
    }
  }
  const slug = recipe.slug
    ? String(recipe.slug).trim()
    : String(recipe.name || "")
        .toLowerCase()
        .replace(/['’]/g, "")
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "");
  if (slug && recipe.id) {
    return "https://www.hellofresh.com/recipes/" + slug + "-" + String(recipe.id).trim();
  }
  return null;
}

/**
 * Exchanges HELLOFRESH_REFRESH_TOKEN in Script Properties for a fresh access_token
 * and saves the rotated refresh_token back to Script Properties.
 */
function getHelloFreshAccessToken(scriptProps) {
  const refreshToken = (scriptProps.getProperty("HELLOFRESH_REFRESH_TOKEN") || "").trim();
  if (!refreshToken) {
    throw new Error(
      "Missing HELLOFRESH_REFRESH_TOKEN in Script Properties."
    );
  }

  const refreshUrl = HELLOFRESH_BASE_GW_URL + "/refresh?locale=en-US&country=US";
  const resp = UrlFetchApp.fetch(refreshUrl, {
    method: "post",
    contentType: "application/json",
    payload: JSON.stringify({ refresh_token: refreshToken }),
    muteHttpExceptions: true,
    headers: {
      "User-Agent": BROWSER_USER_AGENT,
      "Accept": "application/json"
    }
  });

  if (resp.getResponseCode() !== 200) {
    throw new Error(
      "HelloFresh token refresh failed (HTTP " +
        resp.getResponseCode() +
        "): " +
        resp.getContentText()
    );
  }

  const tokenData = JSON.parse(resp.getContentText());
  if (!tokenData.access_token) {
    throw new Error("HelloFresh refresh response did not contain an access_token.");
  }

  // Persist the newly rotated refresh_token so subsequent runs continue working
  if (tokenData.refresh_token && tokenData.refresh_token !== refreshToken) {
    scriptProps.setProperty("HELLOFRESH_REFRESH_TOKEN", tokenData.refresh_token);
  }

  return tokenData.access_token;
}
