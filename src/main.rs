#![feature(question_mark)]

extern crate select;

use std::io::prelude::*;
use std::fs::File;
use std::panic;
mod dotabuff;
mod web;

use select::document::Document;
use select::predicate::{Name, Class, Predicate};

fn main() {
    let mut f = File::open("ayy.html").unwrap();
    let mut s = String::new();
    f.read_to_string(&mut s).unwrap();
    let doc = Document::from(s.as_str());
    println!("{}",
             doc.find(Name("div").and(Class("header-content-title")))
                 .find(Name("h1"))
                 .first()
                 .unwrap()
                 .children()
                 .first()
                 .unwrap()
                 .text());


    panic::catch_unwind(|| {
        panic!("at the disco");
    });
    println!("ayy");
}
