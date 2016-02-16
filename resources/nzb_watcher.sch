--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: backfill_attempt; Type: TABLE; Schema: public; Owner: jockc; Tablespace: 
--

CREATE TABLE backfill_attempt (
    name character varying(1000),
    id integer NOT NULL
);


ALTER TABLE backfill_attempt OWNER TO jockc;

--
-- Name: backfill_attempt_id_seq; Type: SEQUENCE; Schema: public; Owner: jockc
--

CREATE SEQUENCE backfill_attempt_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE backfill_attempt_id_seq OWNER TO jockc;

--
-- Name: backfill_attempt_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: jockc
--

ALTER SEQUENCE backfill_attempt_id_seq OWNED BY backfill_attempt.id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: jockc; Tablespace: 
--

CREATE TABLE categories (
    id integer NOT NULL,
    number character varying(10),
    last_seen_dt timestamp without time zone DEFAULT now()
);


ALTER TABLE categories OWNER TO jockc;

--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: jockc
--

CREATE SEQUENCE categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE categories_id_seq OWNER TO jockc;

--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: jockc
--

ALTER SEQUENCE categories_id_seq OWNED BY categories.id;


--
-- Name: episode_info; Type: TABLE; Schema: public; Owner: jockc; Tablespace: 
--

CREATE TABLE episode_info (
    id integer NOT NULL,
    show_id integer,
    season integer,
    episode_num integer,
    episode_name text
);


ALTER TABLE episode_info OWNER TO jockc;

--
-- Name: episode_info_id_seq; Type: SEQUENCE; Schema: public; Owner: jockc
--

CREATE SEQUENCE episode_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE episode_info_id_seq OWNER TO jockc;

--
-- Name: episode_info_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: jockc
--

ALTER SEQUENCE episode_info_id_seq OWNED BY episode_info.id;


--
-- Name: episodes; Type: TABLE; Schema: public; Owner: jockc; Tablespace: 
--

CREATE TABLE episodes (
    id integer NOT NULL,
    pattern_id integer,
    episode character varying(255),
    loaded_dt timestamp without time zone DEFAULT now()
);


ALTER TABLE episodes OWNER TO jockc;

--
-- Name: episodes_id_seq; Type: SEQUENCE; Schema: public; Owner: jockc
--

CREATE SEQUENCE episodes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE episodes_id_seq OWNER TO jockc;

--
-- Name: episodes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: jockc
--

ALTER SEQUENCE episodes_id_seq OWNED BY episodes.id;


--
-- Name: excludes; Type: TABLE; Schema: public; Owner: jockc; Tablespace: 
--

CREATE TABLE excludes (
    pattern_regex character varying(100),
    id integer NOT NULL
);


ALTER TABLE excludes OWNER TO jockc;

--
-- Name: excludes_id_seq; Type: SEQUENCE; Schema: public; Owner: jockc
--

CREATE SEQUENCE excludes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE excludes_id_seq OWNER TO jockc;

--
-- Name: excludes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: jockc
--

ALTER SEQUENCE excludes_id_seq OWNED BY excludes.id;


--
-- Name: includes; Type: TABLE; Schema: public; Owner: jockc; Tablespace: 
--

CREATE TABLE includes (
    id integer NOT NULL,
    pattern character varying(100),
    extra_pattern character varying(200),
    rageid integer,
    category_id integer,
    target character varying(200),
    prefix character varying(30),
    gather_only_flag boolean DEFAULT false,
    thetvdbid integer
);


ALTER TABLE includes OWNER TO jockc;

--
-- Name: loaded_files; Type: TABLE; Schema: public; Owner: jockc; Tablespace: 
--

CREATE TABLE loaded_files (
    name character varying(1000),
    id integer NOT NULL
);


ALTER TABLE loaded_files OWNER TO jockc;

--
-- Name: loaded_files_id_seq; Type: SEQUENCE; Schema: public; Owner: jockc
--

CREATE SEQUENCE loaded_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE loaded_files_id_seq OWNER TO jockc;

--
-- Name: loaded_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: jockc
--

ALTER SEQUENCE loaded_files_id_seq OWNED BY loaded_files.id;


--
-- Name: loadrec_id_seq; Type: SEQUENCE; Schema: public; Owner: jockc
--

CREATE SEQUENCE loadrec_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE loadrec_id_seq OWNER TO jockc;

--
-- Name: loadrec_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: jockc
--

ALTER SEQUENCE loadrec_id_seq OWNED BY includes.id;


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: jockc
--

ALTER TABLE ONLY backfill_attempt ALTER COLUMN id SET DEFAULT nextval('backfill_attempt_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: jockc
--

ALTER TABLE ONLY categories ALTER COLUMN id SET DEFAULT nextval('categories_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: jockc
--

ALTER TABLE ONLY episode_info ALTER COLUMN id SET DEFAULT nextval('episode_info_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: jockc
--

ALTER TABLE ONLY episodes ALTER COLUMN id SET DEFAULT nextval('episodes_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: jockc
--

ALTER TABLE ONLY excludes ALTER COLUMN id SET DEFAULT nextval('excludes_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: jockc
--

ALTER TABLE ONLY includes ALTER COLUMN id SET DEFAULT nextval('loadrec_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: jockc
--

ALTER TABLE ONLY loaded_files ALTER COLUMN id SET DEFAULT nextval('loaded_files_id_seq'::regclass);


--
-- Name: categories_pkey; Type: CONSTRAINT; Schema: public; Owner: jockc; Tablespace: 
--

ALTER TABLE ONLY categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: excludes_pkey; Type: CONSTRAINT; Schema: public; Owner: jockc; Tablespace: 
--

ALTER TABLE ONLY excludes
    ADD CONSTRAINT excludes_pkey PRIMARY KEY (id);


--
-- Name: loaded_files_pkey; Type: CONSTRAINT; Schema: public; Owner: jockc; Tablespace: 
--

ALTER TABLE ONLY loaded_files
    ADD CONSTRAINT loaded_files_pkey PRIMARY KEY (id);


--
-- Name: loadrec_pkey; Type: CONSTRAINT; Schema: public; Owner: jockc; Tablespace: 
--

ALTER TABLE ONLY includes
    ADD CONSTRAINT loadrec_pkey PRIMARY KEY (id);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

