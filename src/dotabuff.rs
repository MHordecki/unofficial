extern crate select;

use std::io::prelude::*;
use std::fs::File;

use select::document::Document;
use select::predicate::{Name, Class, Predicate};

use web::WebClient;

#[derive(PartialEq, Eq, Debug, Copy, Clone)]
struct TeamId(pub u64);
#[derive(PartialEq, Eq, Debug, Copy, Clone)]
struct SeriesId(pub u64);

#[derive(PartialEq, Eq, Debug)]
struct Series {
    winner: TeamId,
    loser: TeamId,
}

struct Dotabuff<W: WebClient> {
    web: W,
}

impl<W: WebClient> Dotabuff<W> {
    fn get_team_series(&self, team: TeamId, page: u32) -> Vec<Series> {
        let url = match page {
            0 => String::from("http://www.dotabuff.com/esports/teams/1836806/series"),
            _ => format!("http://www.dotabuff.com/esports/teams/1836806/series?page={}", page)
        };
        let html = self.web.get(&url);
        let doc = Document::from(html.as_str());
        let name = doc.find(Name("div").and(Class("header-content-title")))
            .find(Name("h1"))
            .first()
            .unwrap()
            .children()
            .first()
            .unwrap()
            .text();
        return Vec::new();
    }
}

#[cfg(test)]
mod tests {
    use super::{Dotabuff,Series,TeamId};
    use web::TestWebClient;
    use std::path::Path;

    #[test]
    fn test_get_team_series() {
        let db = Dotabuff { web: TestWebClient::new(Path::new("testdata")) };
        let res = db.get_team_series(TeamId(142372), 0);
        assert_eq!(res.len(), 20);
    }
}
