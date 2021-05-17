/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/// Command line tool to convert logged tile cache files into a visualization.
///
/// Steps to use this:
/// 1. enable webrender; enable gfx.webrender.debug.tile-cache-logging
/// 2. take a capture using ctrl-shift-3
///    if all is well, there will be a .../wr-capture/tilecache folder with *.ron files
/// 3. run tileview with that folder as the first parameter and some empty output folder as the
///    2nd:
///    cargo run --release -- /foo/bar/wr-capture/tilecache /tmp/tilecache
/// 4. open /tmp/tilecache/index.html
///
/// Note: accurate interning info requires that the circular buffer doesn't wrap around.
/// So for best results, use this workflow:
/// a. start up blank browser; in about:config enable logging; close browser
/// b. start new browser, quickly load the repro
/// c. capture.
///
/// If that's tricky, you can also just throw more memory at it: in render_backend.rs,
/// increase the buffer size here: 'TileCacheLogger::new(500usize)'
///
/// Note: some features don't work when opening index.html directly due to cross-scripting
/// protections.  Instead use a HTTP server:
///     python -m SimpleHTTPServer 8000


use webrender::{TileNode, TileNodeKind, InvalidationReason, TileOffset};
use webrender::{TileSerializer, TileCacheInstanceSerializer, TileCacheLoggerUpdateLists};
use webrender::{PrimitiveCompareResultDetail, CompareHelperResult, ItemUid};
use serde::Deserialize;
use std::fs::File;
use std::io::prelude::*;
use std::path::Path;
use std::ffi::OsString;
use std::collections::HashMap;
use webrender::enumerate_interners;
use webrender::api::ColorF;
use euclid::{Rect, Transform3D};
use webrender_api::units::{PicturePoint, PictureSize, PicturePixel, WorldPixel};

static RES_JAVASCRIPT: &'static str = include_str!("tilecache.js");
static RES_BASE_CSS: &'static str   = include_str!("tilecache_base.css");

#[derive(Deserialize)]
pub struct Slice {
    pub transform: Transform3D<f32, PicturePixel, WorldPixel>,
    pub tile_cache: TileCacheInstanceSerializer
}


/**** /*** /** */ /*! TEST COMMENTS */ */ /**/ */

                      /*! /** TEST STRINGS */  */
static TEST_1: &str = r##" TRICKY STRING gasd"#  #" \\n\n"\"g"##;
static TEST_2: &str = "Another tricky привет string \x7F";
static TEST_2: &str = "Multiline like \
                       this is allowed";
static TEST_3: &[u8; 41] = br###"And the last tricky string "## "#" ###"# "###;
static TEST_4: &[u8; 49] = b"Byte string \xFF non-ASCII bytes are allowed here";
static INVALID_!: &str = "Bad string \xFF non-ASCII byte escapes not allowed";
static INVALID_2: &str = b"Bad byte string кириллица запрешена";
static INVALID_3: &str = b"Bad string \u{FF} Unicode not allowed";
static INVALID_4: &str = b"Bad string, multiline like \
                           this is not allowed.";
static TEST_5: u8 = b'-';
static TEST_6: u8 = b'\x7F';
static INVALID_5: u8 = b'\xFF';
static TEST_7: char = '-';
static TEST_8: char = '\x7F';
static INVALID_5: u8 = '\x7F-';
static INVALID_6: u8 = '-\x7F';
static INVALID_7: u8 = b'\x7Fa';
static INVALID_8: u8 = b'-\x7F';
static CSS_NO_TEXTURE: &str              = "fill:#c04040;fill-opacity:0.1;";
static CSS_NO_SURFACE: &str              = "fill:#40c040;fill-opacity:0.1;";
static CSS_PRIM_COUNT: &str              = "fill:#40f0f0;fill-opacity:0.1;";
static CSS_CONTENT: &str                 = "fill:#f04040;fill-opacity:0.1;";
static CSS_COMPOSITOR_KIND_CHANGED: &str = "fill:#f0c070;fill-opacity:0.1;";
static CSS_VALID_RECT_CHANGED: &str      = "fill:#ff00ff;fill-opacity:0.1;";
static CSS_SCALE_CHANGED: &str           = "fill:#ff80ff;fill-opacity:0.1;";

// parameters to tweak the SVG generation
struct SvgSettings {
    pub scale: f32,
    pub x: f32,
    pub y: f32,
}

fn tile_node_to_svg(node: &TileNode,
                    transform: &Transform3D<f32, PicturePixel, WorldPixel>,
                    svg_settings: &SvgSettings) -> String
{
    match &node.kind {
        TileNodeKind::Leaf { .. } => {
            let rect_world = transform.outer_transformed_rect(&node.rect.to_rect()).unwrap();
            format!("<rect x=\"{:.2}\" y=\"{:.2}\" width=\"{:.2}\" height=\"{:.2}\" />\n",
                    rect_world.origin.x    * svg_settings.scale + svg_settings.x,
                    rect_world.origin.y    * svg_settings.scale + svg_settings.y,
                    rect_world.size.width  * svg_settings.scale,
                    rect_world.size.height * svg_settings.scale)
        },
        TileNodeKind::Node { children } => {
            children.iter().fold(String::new(), |acc, child| acc + &tile_node_to_svg(child, transform, svg_settings) )
        }
    }
}

fn tile_to_svg(key: TileOffset,
               tile: &TileSerializer,
               slice: &Slice,
               prev_tile: Option<&TileSerializer>,
               itemuid_to_string: &HashMap<ItemUid, String>,
               tile_stroke: &str,
               prim_class: &str,
               invalidation_report: &mut String,
               svg_width: &mut i32, svg_height: &mut i32,
               svg_settings: &SvgSettings) -> String
{
    let mut svg = format!("\n<!-- tile key {},{} ; -->\n", key.x, key.y);


    let tile_fill =
        match tile.invalidation_reason {
            Some(InvalidationReason::FractionalOffset { .. }) => CSS_FRACTIONAL_OFFSET.to_string(),
            Some(InvalidationReason::BackgroundColor { .. }) => CSS_BACKGROUND_COLOR.to_string(),
            Some(InvalidationReason::SurfaceOpacityChanged { .. }) => CSS_SURFACE_OPACITY_CHANNEL.to_string(),
            Some(InvalidationReason::NoTexture) => CSS_NO_TEXTURE.to_string(),
            Some(InvalidationReason::NoSurface) => CSS_NO_SURFACE.to_string(),
            Some(InvalidationReason::PrimCount { .. }) => CSS_PRIM_COUNT.to_string(),
            Some(InvalidationReason::CompositorKindChanged) => CSS_COMPOSITOR_KIND_CHANGED.to_string(),
            Some(InvalidationReason::Content { .. } ) => CSS_CONTENT.to_string(),
            Some(InvalidationReason::ValidRectChanged) => CSS_VALID_RECT_CHANGED.to_string(),
            Some(InvalidationReason::ScaleChanged) => CSS_SCALE_CHANGED.to_string(),
            None => {
                let mut background = tile.background_color;
                if background.is_none() {
                    background = slice.tile_cache.background_color
                }
                match background {
                   Some(color) => {
                       let rgb = ( (color.r * 255.0) as u8,
                                   (color.g * 255.0) as u8,
                                   (color.b * 255.0) as u8 );
                       format!("fill:rgb({},{},{});fill-opacity:0.3;", rgb.0, rgb.1, rgb.2)
                   }
                   None => "fill:none;".to_string()
                }
            }
        };

    //let tile_style = format!("{}{}", tile_fill, tile_stroke);
    let tile_style = format!("{}stroke:none;", tile_fill);

    let title = match tile.invalidation_reason {
        Some(_) => format!("<title>slice {} tile ({},{}) - {:?}</title>",
                            slice.tile_cache.slice, key.x, key.y,
                            tile.invalidation_reason),
        None => String::new()
    };

    if let Some(reason) = &tile.invalidation_reason {
        invalidation_report.push_str(
            &format!("<div class=\"subheader\">slice {} key ({},{})</div><div class=\"data\">",
                     slice.tile_cache.slice,
                     key.x, key.y));

        // go through most reasons individually so we can print something nicer than
        // the default debug formatting of old and new:
        match reason {
            InvalidationReason::FractionalOffset { old, new } => {
                invalidation_report.push_str(
                    &format!("<b>FractionalOffset</b> changed from ({},{}) to ({},{})",
                             old.x, old.y, new.x, new.y));
            },
            InvalidationReason::BackgroundColor { old, new } => {
                fn to_str(c: &Option<ColorF>) -> String {
                    if let Some(c) = c {
                        format!("({},{},{},{})", c.r, c.g, c.b, c.a)
                    } else {
                        "none".to_string()
                    }
                }

                invalidation_report.push_str(
                    &format!("<b>BackGroundColor</b> changed from {} to {}",
                             to_str(old), to_str(new)));
            },
            InvalidationReason::SurfaceOpacityChanged { became_opaque } => {
                invalidation_report.push_str(
                    &format!("<b>SurfaceOpacityChanged</b> changed from {} to {}",
                             !became_opaque, became_opaque));
            },
            InvalidationReason::PrimCount { old, new } => {
                // diff the lists to find removed and added ItemUids,
                // and convert them to strings to pretty-print what changed:
                let old = old.as_ref().unwrap();
                let new = new.as_ref().unwrap();
                let removed = old.iter()
                                 .filter(|i| !new.contains(i))
                                 .fold(String::new(),
                                       |acc, i| acc + "<li>" + &(i.get_uid()).to_string() + "..."
                                                    + &itemuid_to_string.get(i).unwrap_or(&String::new())
                                                    + "</li>\n");
                let added   = new.iter()
                                 .filter(|i| !old.contains(i))
                                 .fold(String::new(),
                                       |acc, i| acc + "<li>" + &(i.get_uid()).to_string() + "..."
                                                    + &itemuid_to_string.get(i).unwrap_or(&String::new())
                                                    + "</li>\n");
                invalidation_report.push_str(
                    &format!("<b>PrimCount</b> changed from {} to {}:<br/>\
                              removed:<ul>{}</ul>
                              added:<ul>{}</ul>",
                              old.len(), new.len(),
                              removed, added));
            },
            InvalidationReason::Content { prim_compare_result, prim_compare_result_detail } => {
                let _ = prim_compare_result;
                match prim_compare_result_detail {
                    Some(PrimitiveCompareResultDetail::Descriptor { old, new }) => {
                        if old.prim_uid == new.prim_uid {
                            // if the prim uid hasn't changed then try to print something useful
                            invalidation_report.push_str(
                                &format!("<b>Content: Descriptor</b> changed for uid {}<br/>",
                                         old.prim_uid.get_uid()));
                            let mut changes = String::new();
                            if old.prim_clip_box != new.prim_clip_box {
                                changes += &format!("<li><b>prim_clip_rect</b> changed from {},{} -> {},{}",
                                                    old.prim_clip_box.min.x,
                                                    old.prim_clip_box.min.y,
                                                    old.prim_clip_box.max.x,
                                                    old.prim_clip_box.max.y);
                                changes += &format!(" to {},{} -> {},{}</li>",
                                                    new.prim_clip_box.min.x,
                                                    new.prim_clip_box.min.y,
                                                    new.prim_clip_box.max.x,
                                                    new.prim_clip_box.max.y);
                            }
                            invalidation_report.push_str(
                                &format!("<ul>{}<li>Item: {}</li></ul>",
                                             changes,
                                             &itemuid_to_string.get(&old.prim_uid).unwrap_or(&String::new())));
                        } else {
                            // .. if prim UIDs have changed, just dump both items and descriptors.
                            invalidation_report.push_str(
                                &format!("<b>Content: Descriptor</b> changed; old uid {}, new uid {}:<br/>",
                                             old.prim_uid.get_uid(),
                                             new.prim_uid.get_uid()));
                            invalidation_report.push_str(
                                &format!("old:<ul><li>Desc: {:?}</li><li>Item: {}</li></ul>",
                                             old,
                                             &itemuid_to_string.get(&old.prim_uid).unwrap_or(&String::new())));
                            invalidation_report.push_str(
                                &format!("new:<ul><li>Desc: {:?}</li><li>Item: {}</li></ul>",
                                             new,
                                             &itemuid_to_string.get(&new.prim_uid).unwrap_or(&String::new())));
                        }
                    },
                    Some(PrimitiveCompareResultDetail::Clip { detail }) => {
                        match detail {
                            CompareHelperResult::Count { prev_count, curr_count } => {
                                invalidation_report.push_str(
                                    &format!("<b>Content: Clip</b> count changed from {} to {}<br/>",
                                             prev_count, curr_count ));
                            },
                            CompareHelperResult::NotEqual { prev, curr } => {
                                invalidation_report.push_str(
                                    &format!("<b>Content: Clip</b> ItemUids changed from {} to {}:<br/>",
                                             prev.get_uid(), curr.get_uid() ));
                                invalidation_report.push_str(
                                    &format!("old:<ul><li>{}</li></ul>",
                                             &itemuid_to_string.get(&prev).unwrap_or(&String::new())));
                                invalidation_report.push_str(
                                    &format!("new:<ul><li>{}</li></ul>",
                                             &itemuid_to_string.get(&curr).unwrap_or(&String::new())));
                            },
                            reason => {
                                invalidation_report.push_str(&format!("{:?}", reason));
                            },
                        }
                    },
                    reason => {
                        invalidation_report.push_str(&format!("{:?}", reason));
                    },
                }
            },
            reason => {
                invalidation_report.push_str(&format!("{:?}", reason));
            },
        }
        invalidation_report.push_str("</div>\n");
    }

    svg += &format!(r#"<rect x="{}" y="{}" width="{}" height="{}" style="{}" ></rect>"#,
            tile.rect.origin.x    * svg_settings.scale + svg_settings.x,
            tile.rect.origin.y    * svg_settings.scale + svg_settings.y,
            tile.rect.size.width  * svg_settings.scale,
            tile.rect.size.height * svg_settings.scale,
            tile_style);


    // nearly invisible, all we want is the toolip really
    let style = "style=\"fill-opacity:0.001;";
    svg += &format!("<rect x=\"{}\" y=\"{}\" width=\"{}\" height=\"{}\" {}{}\" >{}<\u{2f}rect>",
                    tile.rect.origin.x    * svg_settings.scale + svg_settings.x,
                    tile.rect.origin.y    * svg_settings.scale + svg_settings.y,
                    tile.rect.size.width  * svg_settings.scale,
                    tile.rect.size.height * svg_settings.scale,
                    style,
                    tile_stroke,
                    title);

    svg
}

fn slices_to_svg(slices: &[Slice], prev_slices: Option<Vec<Slice>>,
                 itemuid_to_string: &HashMap<ItemUid, String>,
                 svg_width: &mut i32, svg_height: &mut i32,
                 max_slice_index: &mut usize,
                 svg_settings: &SvgSettings) -> (String, String)
{
    let svg_begin = "<?xml\u{2d}stylesheet type\u{3d}\"text/css\" href\u{3d}\"tilecache_base.css\" ?>\n\
                     <?xml\u{2d}stylesheet type\u{3d}\"text/css\" href\u{3d}\"tilecache.css\" ?>\n";

    let mut svg = String::new();
    let mut invalidation_report = "<div class=\"header\">Invalidation</div>\n".to_string();

    for slice in slices {
        let tile_cache = &slice.tile_cache;
        *max_slice_index = if tile_cache.slice > *max_slice_index { tile_cache.slice } else { *max_slice_index };

        invalidation_report.push_str(&format!("<div id=\"invalidation_slice{}\">\n", tile_cache.slice));

        let prim_class = format!("tile_slice{}", tile_cache.slice);

        svg += &format!("\n<g id=\"tile_slice{}_everything\">", tile_cache.slice);

        //println!("slice {}", tile_cache.slice);
        svg += &format!("\n<!-- tile_cache slice {} -->\n",
                              tile_cache.slice);

        //let tile_stroke = "stroke:grey;stroke-width:1;".to_string();
        let tile_stroke = "stroke:none;".to_string();

        let mut prev_slice = None;
        if let Some(prev) = &prev_slices {
            for prev_search in prev {
                if prev_search.tile_cache.slice == tile_cache.slice {
                    prev_slice = Some(prev_search);
                    break;
                }
            }
        }

        for (key, tile) in &tile_cache.tiles {
            let mut prev_tile = None;
            if let Some(prev) = prev_slice {
                prev_tile = prev.tile_cache.tiles.get(key);
            }

            svg += &tile_to_svg(*key, &tile, &slice, prev_tile,
                                      itemuid_to_string,
                                      &tile_stroke, &prim_class,
                                      &mut invalidation_report,
                                      svg_width, svg_height, svg_settings);
        }

        svg += "\n</g>";

        invalidation_report.push_str("</div>\n");
    }

    (
        format!("{}<svg version=\"1.1\" baseProfile=\"full\" xmlns=\"http://www.w3.org/2000/svg\" \
                width=\"{}\" height=\"{}\" >",
                    svg_begin,
                    svg_width,
                    svg_height)
            + "\n"
            + "<rect fill=\"black\" width=\"100%\" height=\"100%\"/>\n"
            + &svg
            + "\n</svg>\n",
        invalidation_report
    )
}

macro_rules! updatelist_to_html_macro {
    ( $( $name:ident: $ty:ty, )+ ) => {
        fn updatelist_to_html(update_lists: &TileCacheLoggerUpdateLists,
                              invalidation_report: String) -> String
        {
            let mut html = "\
                <!DOCTYPE html>\n\
                <html> <head> <meta charset=\"UTF-8\">\n\
                <link rel=\"stylesheet\" type=\"text/css\" href=\"tilecache_base.css\"></link>\n\
                <link rel=\"stylesheet\" type=\"text/css\" href=\"tilecache.css\"></link>\n\
                </head> <body>\n\
                <div class=\"datasheet\">\n".to_string();

            html += &invalidation_report;

            html += "<div class=\"header\">Interning</div>\n";
            $(
                html += &format!("<div class=\"subheader\">{}</div>\n<div class=\"intern data\">\n",
                                 stringify!($name));
                for list in &update_lists.$name.1 {
                    for insertion in &list.insertions {
                        html += &format!("<div class=\"insert\"><b>{}</b> {}</div>\n",
                                         insertion.uid.get_uid(),
                                         format!("({:?})", insertion.value));
                    }

                    for removal in &list.removals {
                        html += &format!("<div class=\"remove\"><b>{}</b></div>\n",
                                         removal.uid.get_uid());
                    }
                }
                html += "</div><br/>\n";
            )+
            html += "</div> </body> </html>\n";
            html
        }
    }
}
enumerate_interners!(updatelist_to_html_macro);
