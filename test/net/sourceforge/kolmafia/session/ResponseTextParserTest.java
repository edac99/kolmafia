package net.sourceforge.kolmafia.session;

import static internal.helpers.Networking.assertPostRequest;
import static internal.helpers.Networking.html;
import static internal.helpers.Player.withGender;
import static internal.helpers.Player.withHttpClientBuilder;
import static internal.helpers.Player.withItem;
import static internal.helpers.Player.withPasswordHash;
import static internal.helpers.Player.withProperty;
import static internal.matchers.Preference.isSetTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

import internal.helpers.Cleanups;
import internal.network.FakeHttpClientBuilder;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLCharacter.Gender;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.objectpool.SkillPool;
import net.sourceforge.kolmafia.preferences.Preferences;
import net.sourceforge.kolmafia.request.GenericRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResponseTextParserTest {
  @BeforeEach
  public void beforeEach() {
    KoLCharacter.reset("ResponseTextParserTest");
    Preferences.reset("ResponseTextParserTest");
  }

  @ParameterizedTest
  @ValueSource(ints = {SkillPool.SLIMY_SHOULDERS, SkillPool.SLIMY_SYNAPSES, SkillPool.SLIMY_SINEWS})
  void canLearnSlimeSkills(int skillId) {
    var levelPref = "skillLevel" + skillId;
    var cleanups = new Cleanups(withProperty(levelPref, 1));
    try (cleanups) {
      ResponseTextParser.learnSkill(skillId);
      assertThat(levelPref, isSetTo(2));
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {SkillPool.SLIMY_SHOULDERS, SkillPool.SLIMY_SYNAPSES, SkillPool.SLIMY_SINEWS})
  void cannotOverlearnSlimeSkills(int skillId) {
    var levelPref = "skillLevel" + skillId;
    var cleanups = new Cleanups(withProperty(levelPref, 10));
    try (cleanups) {
      ResponseTextParser.learnSkill(skillId);
      assertThat(levelPref, isSetTo(10));
    }
  }

  @Test
  void canParseLatte() {
    String responseText = html("request/test_latte_description.html");
    ResponseTextParser.externalUpdate("desc_item.php?whichitem=294224337", responseText);
    assertEquals(Preferences.getString("latteIngredients"), "pumpkin,carrot,cinnamon");
  }

  @Nested
  class Recipes {
    @Test
    public void canLearnRecipeFromItem() {
      var builder = new FakeHttpClientBuilder();
      var client = builder.client;
      AdventureResult recipe = ItemPool.get(ItemPool.ROBY_PETES_WILY_WHEY_BAR);
      var cleanups =
          new Cleanups(
              withHttpClientBuilder(builder),
              withPasswordHash("recipe"),
              // If you have a password hash, KoL looks at your vinyl boots
              withGender(Gender.FEMALE),
              withItem(recipe),
              withProperty("unknownRecipe10974", true),
              withProperty("_concoctionDatabaseRefreshes", 0));
      try (cleanups) {
        client.addResponse(200, html("request/test_learn_recipe.html"));
        client.addResponse(200, ""); // api.php

        String URL = "inv_use.php?which=3&whichitem=10983&pwd&ajax=1";
        var request = new GenericRequest(URL);
        request.run();

        // It consumed our recipe
        assertEquals(0, InventoryManager.getCount(recipe));

        // Learned recipe: Pete's wiley whey bar (10974)
        assertThat("unknownRecipe10974", isSetTo(false));
        assertThat("_concoctionDatabaseRefreshes", isSetTo(1));

        var requests = client.getRequests();
        assertThat(requests, hasSize(2));
        assertPostRequest(
            requests.get(0), "/inv_use.php", "which=3&whichitem=10983&ajax=1&pwd=recipe");
        assertPostRequest(requests.get(1), "/api.php", "what=status&for=KoLmafia");
      }
    }

    @Test
    public void canDetectPreviouslyLearnedRecipeFromItem() {
      var builder = new FakeHttpClientBuilder();
      var client = builder.client;
      AdventureResult recipe = ItemPool.get(ItemPool.ROBY_PETES_WILY_WHEY_BAR);
      var cleanups =
          new Cleanups(
              withHttpClientBuilder(builder),
              withPasswordHash("recipe"),
              // If you have a password hash, KoL looks at your vinyl boots
              withGender(Gender.FEMALE),
              withItem(recipe),
              withProperty("unknownRecipe10974", true),
              withProperty("_concoctionDatabaseRefreshes", 0));
      try (cleanups) {
        client.addResponse(200, html("request/test_learn_recipe_twice.html"));
        client.addResponse(200, ""); // api.php

        String URL = "inv_use.php?which=3&whichitem=10983&pwd&ajax=1";
        var request = new GenericRequest(URL);
        request.run();

        // It did not consume our recipe
        assertEquals(1, InventoryManager.getCount(recipe));

        // Learned recipe: Pete's wiley whey bar (10974)
        assertThat("unknownRecipe10974", isSetTo(false));
        assertThat("_concoctionDatabaseRefreshes", isSetTo(1));

        var requests = client.getRequests();
        assertThat(requests, hasSize(2));
        assertPostRequest(
            requests.get(0), "/inv_use.php", "which=3&whichitem=10983&ajax=1&pwd=recipe");
        assertPostRequest(requests.get(1), "/api.php", "what=status&for=KoLmafia");
      }
    }
  }
}
