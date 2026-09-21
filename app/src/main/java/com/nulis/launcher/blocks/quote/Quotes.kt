// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.quote

import java.time.LocalDate

/** A line and who said it. [author] may be blank for something the user wrote themselves. */
data class Quote(val text: String, val author: String)

/**
 * The bundled set. Everything here is from a work long in the public domain - the newest author
 * died in 1900 - and each line is a sentence or two, quoted with attribution. Nulis never
 * fetches a quote from anywhere; this list is the whole of it unless the user adds their own.
 */
val BundledQuotes: List<Quote> = listOf(
    Quote("You have power over your mind, not outside events. Realize this, and you will find strength.", "Marcus Aurelius"),
    Quote("Waste no more time arguing what a good man should be. Be one.", "Marcus Aurelius"),
    Quote("We suffer more often in imagination than in reality.", "Seneca"),
    Quote("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"),
    Quote("Simplify, simplify.", "Henry David Thoreau"),
    Quote("It is not enough to be busy. The question is: what are we busy about?", "Henry David Thoreau"),
    Quote("Nothing great was ever achieved without enthusiasm.", "Ralph Waldo Emerson"),
    Quote("Do not go where the path may lead; go instead where there is no path.", "Ralph Waldo Emerson"),
    Quote("The journey of a thousand miles begins with a single step.", "Lao Tzu"),
    Quote("Nature does not hurry, yet everything is accomplished.", "Lao Tzu"),
    Quote("It does not matter how slowly you go so long as you do not stop.", "Confucius"),
    Quote("Life is really simple, but we insist on making it complicated.", "Confucius"),
    Quote("My life has been full of terrible misfortunes, most of which never happened.", "Michel de Montaigne"),
    Quote("The greatest thing in the world is to know how to belong to oneself.", "Michel de Montaigne"),
    Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"),
    Quote("Be yourself; everyone else is already taken.", "Oscar Wilde"),
    Quote("We are all in the gutter, but some of us are looking at the stars.", "Oscar Wilde"),
    Quote("Whatever you can do, or dream you can, begin it.", "Johann Wolfgang von Goethe"),
    Quote("To be idle requires a strong sense of personal identity.", "Robert Louis Stevenson"),
    Quote("Don't judge each day by the harvest you reap but by the seeds that you plant.", "Robert Louis Stevenson"),
    Quote("The secret of getting ahead is getting started.", "Mark Twain"),
    Quote("Continuous improvement is better than delayed perfection.", "Mark Twain"),
    Quote("Dwell in possibility.", "Emily Dickinson"),
    Quote("Hope is the thing with feathers that perches in the soul.", "Emily Dickinson"),
    Quote("There is no charm equal to tenderness of heart.", "Jane Austen"),
    Quote("It is not time or opportunity that is to determine intimacy; it is disposition alone.", "Jane Austen"),
    Quote("A room without books is like a body without a soul.", "Cicero"),
    Quote("While we teach, we learn.", "Seneca"),
    Quote("The best time to plant a tree was twenty years ago. The second best time is now.", "Proverb"),
    Quote("Fall seven times, stand up eight.", "Japanese proverb"),
    Quote("Nothing is softer or more flexible than water, yet nothing can resist it.", "Lao Tzu"),
    Quote("Work is love made visible.", "Kahlil Gibran"),
)

/**
 * The quote for a given day: the same one all day, a different one tomorrow, and an even spread
 * across the list rather than a run of repeats. [shuffle] steps forward from there, for the tap.
 */
fun quoteFor(date: LocalDate, pool: List<Quote>, shuffle: Int = 0): Quote? {
    if (pool.isEmpty()) return null
    // A large odd multiplier keeps consecutive days far apart in the list.
    val index = ((date.toEpochDay() * 7919 + shuffle).mod(pool.size.toLong())).toInt()
    return pool[index]
}
