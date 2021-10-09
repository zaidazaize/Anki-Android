/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.backend.exception.DeckRenameException;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.utils.JSONObject.NULL;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class CardTest extends RobolectricTest {
    /******************
     ** autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     ******************/

    @Test
    public void test_delete() {
        Collection col = getCol();
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "2");
        col.addNote(note);
        long cid = note.cards().get(0).getId();
        col.reset();
        col.getSched().answerCard(col.getSched().getCard(), 2);
        col.remCards(Collections.singletonList(cid));
        assertEquals(0, col.cardCount());
        assertEquals(0, col.noteCount());
        assertEquals(0, col.getDb().queryScalar("select count() from notes"));
        assertEquals(0, col.getDb().queryScalar("select count() from cards"));
        assertEquals(2, col.getDb().queryScalar("select count() from graves"));
    }


    @Test
    public void test_misc_cards() {
        Collection col = getCol();
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "2");
        col.addNote(note);
        Card c = note.cards().get(0);
        long id = col.getModels().current().getLong("id");
        assertEquals(0, c.template().getInt("ord"));
    }


    @Test
    public void test_genrem() {
        Collection col = getCol();
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "");
        col.addNote(note);
        assertEquals(1, note.numberOfCards());
        Model m = col.getModels().current();
        ModelManager mm = col.getModels();
        // adding a new template should automatically create cards
        JSONObject t = Models.newTemplate("rev");
        t.put("qfmt", "{{Front}}");
        t.put("afmt", "");
        mm.addTemplateModChanged(m, t);
        mm.save(m, true);
        assertEquals(2, note.numberOfCards());
        // if the template is changed to remove cards, they'll be removed
        t = m.getJSONArray("tmpls").getJSONObject(1);
        t.put("qfmt", "{{Back}}");
        mm.save(m, true);
        List<Long> rep = col.emptyCids(null);
        col.remCards(rep);
        assertEquals(1, note.numberOfCards());
        // if we add to the note, a card should be automatically generated
        note.load();
        note.setItem("Back", "1");
        note.flush();
        assertEquals(2, note.numberOfCards());
    }


    @Test
    public void test_gendeck() {
        Collection col = getCol();
        Model cloze = col.getModels().byName("Cloze");
        col.getModels().setCurrent(cloze);
        Note note = col.newNote();
        note.setItem("Text", "{{c1::one}}");
        col.addNote(note);
        assertEquals(1, col.cardCount());
        assertEquals(1, note.cards().get(0).getDid());
        // set the model to a new default col
        long newId = addDeck("new");
        cloze.put("did", newId);
        col.getModels().save(cloze, false);
        // a newly generated card should share the first card's col
        note.setItem("Text", "{{c2::two}}");
        note.flush();
        assertEquals(1, note.cards().get(1).getDid());
        // and same with multiple cards
        note.setItem("Text", "{{c3::three}}");
        note.flush();
        assertEquals(1, note.cards().get(2).getDid());
        // if one of the cards is in a different col, it should revert to the
        // model default
        Card c = note.cards().get(1);
        c.setDid(newId);
        c.flush();
        note.setItem("Text", "{{c4::four}}");
        note.flush();
        assertEquals(newId, note.cards().get(3).getDid());
    }

    @Test
    public void test_gen_or() throws ConfirmModSchemaException {
        Collection col = getCol();
        ModelManager models = col.getModels();
        Model model = models.byName("Basic");
        assertNotNull(model);
        models.renameField(model, model.getJSONArray("flds").getJSONObject(0), "A");
        models.renameField(model, model.getJSONArray("flds").getJSONObject(1), "B");
        JSONObject fld2 = models.newField("C");
        fld2.put("ord", NULL);
        models.addField(model, fld2);

        JSONArray tmpls = model.getJSONArray("tmpls");
        tmpls.getJSONObject(0).put("qfmt", "{{A}}{{B}}{{C}}");
        // ensure first card is always generated,
        // because at last one card is generated
        JSONObject tmpl = Models.newTemplate("AND_OR");
        tmpl.put("qfmt", "        {{A}}    {{#B}}        {{#C}}            {{B}}        {{/C}}    {{/B}}");
        models.addTemplate(model, tmpl);

        models.save(model);
        models.setCurrent(model);

        Note note = col.newNote();
        note.setItem("A", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0, 1});

        note = col.newNote();
        note.setItem("B", "foo");
        note.setItem("C", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0, 1});

        note = col.newNote();
        note.setItem("B", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0});

        note = col.newNote();
        note.setItem("C", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0});

        note = col.newNote();
        note.setItem("A", "foo");
        note.setItem("B", "foo");
        note.setItem("C", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0, 1});

        note = col.newNote();
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0});
        // First card is generated if no other card
    }

    @Test
    public void test_gen_not() throws ConfirmModSchemaException {
        Collection col = getCol();
        ModelManager models = col.getModels();
        Model model = models.byName("Basic");
        assertNotNull(model);
        JSONArray tmpls = model.getJSONArray("tmpls");
        models.renameField(model, model.getJSONArray("flds").getJSONObject(0), "First");
        models.renameField(model, model.getJSONArray("flds").getJSONObject(1), "Front");
        JSONObject fld2 = models.newField("AddIfEmpty");
        fld2.put("name", "AddIfEmpty");
        models.addField(model, fld2);

        // ensure first card is always generated,
        // because at last one card is generated
        tmpls.getJSONObject(0).put("qfmt", "{{AddIfEmpty}}{{Front}}{{First}}");
        JSONObject tmpl = Models.newTemplate("NOT");
        tmpl.put("qfmt", "    {{^AddIfEmpty}}        {{Front}}    {{/AddIfEmpty}}    ");

        models.addTemplate(model, tmpl);

        models.save(model);
        models.setCurrent(model);

        Note note = col.newNote();
        note.setItem("First", "foo");
        note.setItem("AddIfEmpty", "foo");
        note.setItem("Front", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0});

        note = col.newNote();
        note.setItem("First", "foo");
        note.setItem("AddIfEmpty", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0});

        note = col.newNote();
        note.setItem("First", "foo"); // ensure first note generated
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0});

        note = col.newNote();
        note.setItem("First", "foo");
        note.setItem("Front", "foo");
        col.addNote(note);
        assertNoteOrdinalAre(note, new Integer[]{0, 1});
    }

    private void  assertNoteOrdinalAre(Note note, Integer[] ords) {
        ArrayList<Card> cards = note.cards();
        assumeThat(cards.size(), is(ords.length));
        for (Card card: cards) {
            Integer ord = card.getOrd();
            assumeThat(ords, hasItemInArray(ord));
        }
    }

    @Test
    @Config(qualifiers = "en")
    public void nextDueTest() throws DeckRenameException {
        Collection col = getCol();
        // Test runs as the 7th of august 2020, 9h00
        Note n = addNoteUsingBasicModel("Front", "Back");
        Card c = n.firstCard();
        DeckManager decks = col.getDecks();

        Calendar cal = Calendar.getInstance();
        cal.set(2021, 2, 19, 7, 42, 42);
        Long id = cal.getTimeInMillis() / 1000;

        // Not filtered


        c.setType(Consts.CARD_TYPE_NEW);
        c.setDue(27L);

        c.setQueue(Consts.QUEUE_TYPE_MANUALLY_BURIED);
        assertEquals("27", c.nextDue());
        assertEquals("(27)", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SIBLING_BURIED);
        assertEquals("27", c.nextDue());
        assertEquals("(27)", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SUSPENDED);
        assertEquals("27", c.nextDue());
        assertEquals("(27)", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_NEW);
        c.setDue(27L);
        assertEquals("27", c.nextDue());
        assertEquals("27", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_PREVIEW);
        assertEquals("27", c.nextDue());
        assertEquals("27", c.getDueString());


        c.setType(Consts.CARD_TYPE_LRN);
        c.setDue(id);

        c.setQueue(Consts.QUEUE_TYPE_MANUALLY_BURIED);
        assertEquals("", c.nextDue());
        assertEquals("()", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SIBLING_BURIED);
        assertEquals("", c.nextDue());
        assertEquals("()", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SUSPENDED);
        assertEquals("", c.nextDue());
        assertEquals("()", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_LRN);
        assertEquals("3/19/21", c.nextDue());
        assertEquals("3/19/21", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_PREVIEW);
        assertEquals("", c.nextDue());
        assertEquals("", c.getDueString());



        c.setType(Consts.CARD_TYPE_REV);
        c.setDue(20);
        // Since tests run the 7th of august, in 20 days we are the 27th of august 2020

        c.setQueue(Consts.QUEUE_TYPE_MANUALLY_BURIED);
        assertEquals("8/27/20", c.nextDue());
        assertEquals("(8/27/20)", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SIBLING_BURIED);
        assertEquals("8/27/20", c.nextDue());
        assertEquals("(8/27/20)", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SUSPENDED);
        assertEquals("8/27/20", c.nextDue());
        assertEquals("(8/27/20)", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_REV);
        assertEquals("8/27/20", c.nextDue());
        assertEquals("8/27/20", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_PREVIEW);
        assertEquals("", c.nextDue());
        assertEquals("", c.getDueString());



        c.setType(Consts.CARD_TYPE_RELEARNING);
        c.setDue(id);

        c.setQueue(Consts.QUEUE_TYPE_MANUALLY_BURIED);
        assertEquals("", c.nextDue());
        assertEquals("()", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SIBLING_BURIED);
        assertEquals("", c.nextDue());
        assertEquals("()", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SUSPENDED);
        assertEquals("", c.nextDue());
        assertEquals("()", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_LRN);
        c.setDue(id);
        assertEquals("3/19/21", c.nextDue());
        assertEquals("3/19/21", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_PREVIEW);
        assertEquals("", c.nextDue());
        assertEquals("", c.getDueString());




        // Dynamic deck
        long dyn = decks.newDyn("dyn");
        c.setODid(c.getDid());
        c.setDid(dyn);
        assertEquals("(filtered)", c.nextDue());
        assertEquals("(filtered)", c.getDueString());

        c.setQueue(Consts.QUEUE_TYPE_SIBLING_BURIED);
        assertEquals("(filtered)", c.nextDue());
        assertEquals("((filtered))", c.getDueString());



    }

}
