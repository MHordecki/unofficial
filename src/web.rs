extern crate url;

use std::convert::Into;
use std::path::{Path, PathBuf};

pub trait WebClient {
    fn get(&self, url: &str) -> String;
}

pub struct TestWebClient {
    path: PathBuf,
}

impl TestWebClient {
    pub fn new(path: &Path) -> TestWebClient {
        TestWebClient { path: path.to_path_buf() }
    }
}

impl WebClient for TestWebClient {
    fn get(&self, url: &str) -> String {
        return String::from("Fuck");
    }
}
